#!/usr/bin/env python3
"""
Uso:
    python process_audio.py [--watch] [--folder FOLDER_PATH]
    
    --watch: Modo de monitoreo continuo (para Raspberry Pi)
    --folder: Carpeta a monitorear (por defecto: ./audio_files/)
"""

import os
import sys
import sqlite3
import numpy as np
import librosa
import re
import argparse
from datetime import datetime
import time
import logging
from pathlib import Path
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from datetime import datetime, timedelta

# Configuración de logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('audio_processing.log'),
        logging.StreamHandler(sys.stdout)
    ]
)

logger = logging.getLogger(__name__)

class AudioProcessor:
    """Clase para procesar archivos de audio y extraer métricas de ruido."""
    
    def __init__(self, db_path='noise_data.db'):
        self.db_path = db_path
        self.init_database()
    
    def init_database(self):
        """Inicializa la base de datos SQLite con la tabla de métricas."""
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS audio_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp DATETIME NOT NULL,
                    filename TEXT NOT NULL,
                    dbfs_level REAL NOT NULL,
                    rms_energy REAL,
                    dominant_frequency REAL,
                    spectral_centroid REAL,
                    spectral_rolloff REAL,
                    zero_crossing_rate REAL,
                    duration REAL NOT NULL,
                    sample_rate INTEGER NOT NULL,
                    processed_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            conn.commit()
            conn.close()
            logger.info("Base de datos inicializada correctamente")
            
        except Exception as e:
            logger.error(f"Error al inicializar la base de datos: {e}")
            raise
    
    def calculate_dbfs(self, audio_data):
        """
        Calcula el nivel dBFS (Decibeles Full Scale) del audio.
        
        Args:
            audio_data (numpy.ndarray): Datos de audio normalizados
            
        Returns:
            float: Nivel dBFS
        """
        # Calcular RMS (Root Mean Square) de la señal
        rms = np.sqrt(np.mean(audio_data**2))
        
        # Evitar log de cero añadiendo un pequeño valor
        if rms == 0:
            rms = 1e-10
        
        # Calcular dBFS
        dbfs = 20 * np.log10(rms)
        
        return dbfs
    

    def parse_timestamp_from_filename(self, filename):
        """
        Extrae el timestamp de un nombre de archivo con formato:
        'Rec YYYY-MM-DD HHhMMmSSs ...'

        Args:
            filename (str): Nombre del archivo (con o sin extensión)

        Returns:
            datetime: Timestamp extraído
        """
        try:
            # Quitar extensión si existe
            name = os.path.splitext(filename)[0]

            # Regex para capturar fecha y hora
            # Ejemplo que matchea: Rec 2025-06-16 16h36m00s 1
            pattern = r"Rec (\d{4}-\d{2}-\d{2}) (\d{2})h(\d{2})m(\d{2})s"
            match = re.search(pattern, name)

            if match:
                date_str, hour, minute, second = match.groups()
                datetime_str = f"{date_str} {hour}:{minute}:{second}"
                return datetime.strptime(datetime_str, "%Y-%m-%d %H:%M:%S")

        except Exception as e:
            logger.warning(f"No se pudo parsear timestamp desde {filename}: {e}")

        # Fallback → timestamp del archivo
        return datetime.fromtimestamp(os.path.getmtime(filename))

    
    def extract_audio_features(self, file_path):
        """
        Extrae características de audio de un archivo .wav.
        Si el archivo está vacío o contiene solo silencio, devuelve métricas con valores bajos.
        """

        try:
            # Cargar audio con soundfile (más robusto para detectar vacíos)
            import soundfile as sf
            audio_data, sample_rate = sf.read(file_path)

            # Asegurar que sea mono
            if audio_data.ndim > 1:
                audio_data = np.mean(audio_data, axis=1)

            duration = len(audio_data) / sample_rate if sample_rate > 0 else 0

            # Caso: archivo realmente vacío
            if audio_data.size == 0 or duration == 0:
                logger.warning(f"Archivo vacío detectado: {file_path} métricas bajas asignadas")
                return {
                    'timestamp': self.parse_timestamp_from_filename(os.path.basename(file_path)),
                    'filename': os.path.basename(file_path),
                    'dbfs_level': -100.0,
                    'rms_energy': 0.0,
                    'dominant_frequency': 0.0,
                    'spectral_centroid': 0.0,
                    'spectral_rolloff': 0.0,
                    'zero_crossing_rate': 0.0,
                    'duration': float(duration),
                    'sample_rate': int(sample_rate)
                }

            # Caso: audio cargado pero es puro silencio
            rms_energy = np.sqrt(np.mean(audio_data**2))
            if rms_energy < 1e-10:
                logger.info(f"Archivo con silencio detectado: {file_path}")
                return {
                    'timestamp': self.parse_timestamp_from_filename(os.path.basename(file_path)),
                    'filename': os.path.basename(file_path),
                    'dbfs_level': -100.0,
                    'rms_energy': 0.0,
                    'dominant_frequency': 0.0,
                    'spectral_centroid': 0.0,
                    'spectral_rolloff': 0.0,
                    'zero_crossing_rate': 0.0,
                    'duration': float(duration),
                    'sample_rate': int(sample_rate)
                }

            # Caso: audio válido
            dbfs_level = self.calculate_dbfs(audio_data)

            spectral_centroid = np.mean(librosa.feature.spectral_centroid(y=audio_data, sr=sample_rate)[0])
            spectral_rolloff = np.mean(librosa.feature.spectral_rolloff(y=audio_data, sr=sample_rate)[0])
            zero_crossing_rate = np.mean(librosa.feature.zero_crossing_rate(audio_data)[0])

            # FFT para frecuencia dominante
            fft = np.fft.fft(audio_data)
            magnitude = np.abs(fft)
            freq_bins = np.fft.fftfreq(len(fft), 1 / sample_rate)
            dominant_freq_idx = np.argmax(magnitude[:len(magnitude) // 2])
            dominant_frequency = abs(freq_bins[dominant_freq_idx])

            timestamp = self.parse_timestamp_from_filename(os.path.basename(file_path))

            return {
                'timestamp': timestamp,
                'filename': os.path.basename(file_path),
                'dbfs_level': float(dbfs_level),
                'rms_energy': float(rms_energy),
                'dominant_frequency': float(dominant_frequency),
                'spectral_centroid': float(spectral_centroid),
                'spectral_rolloff': float(spectral_rolloff),
                'zero_crossing_rate': float(zero_crossing_rate),
                'duration': float(duration),
                'sample_rate': int(sample_rate)
            }

        except Exception as e:
            logger.error(f"Error al procesar {file_path}: {e}")
            # Si algo inesperado pasa, devolvemos métricas vacías en lugar de None
            return {
                'timestamp': datetime.fromtimestamp(os.path.getmtime(file_path)),
                'filename': os.path.basename(file_path),
                'dbfs_level': -100.0,
                'rms_energy': 0.0,
                'dominant_frequency': 0.0,
                'spectral_centroid': 0.0,
                'spectral_rolloff': 0.0,
                'zero_crossing_rate': 0.0,
                'duration': 0.0,
                'sample_rate': 0
            }


        
    def save_metrics_to_db(self, metrics):
        """
        Guarda las métricas en la base de datos.
        
        Args:
            metrics (dict): Diccionario con las métricas calculadas
        """
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                INSERT INTO audio_metrics (
                    timestamp, filename, dbfs_level, rms_energy, dominant_frequency,
                    spectral_centroid, spectral_rolloff, zero_crossing_rate,
                    duration, sample_rate
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                metrics['timestamp'],
                metrics['filename'],
                metrics['dbfs_level'],
                metrics['rms_energy'],
                metrics['dominant_frequency'],
                metrics['spectral_centroid'],
                metrics['spectral_rolloff'],
                metrics['zero_crossing_rate'],
                metrics['duration'],
                metrics['sample_rate']
            ))
            
            conn.commit()
            conn.close()
            
            logger.info(f"Métricas guardadas para {metrics['filename']}")
            
        except Exception as e:
            logger.error(f"Error al guardar métricas: {e}")
    
    def process_audio_file(self, file_path):
        """
        Procesa un único archivo de audio.
        
        Args:
            file_path (str): Ruta al archivo de audio
        """
        if not file_path.lower().endswith('.wav'):
            logger.warning(f"Archivo ignorado (no es .wav): {file_path}")
            return
        
        logger.info(f"Procesando: {file_path}")
        
        metrics = self.extract_audio_features(file_path)
        if metrics:
            #self.save_metrics_to_db(metrics)
            print(metrics)
            logger.info(f"Procesamiento completado: {os.path.basename(file_path)}")
            self.cleanup_processed_file(file_path)
        else:
            logger.error(f"Falló el procesamiento: {file_path}")

    
    def cleanup_processed_file(self, file_path: str):
        """
        Elimina el archivo de audio inmediatamente después de procesarlo 
        y guardar sus métricas en la base de datos.

        Args:
            file_path (str): Ruta al archivo procesado
        """
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
                logger.info(f"Archivo eliminado después de procesar: {file_path}")
            else:
                logger.warning(f"Archivo no encontrado al intentar eliminar: {file_path}")
        except Exception as e:
            logger.error(f"Error al eliminar archivo {file_path}: {e}")

    
    def process_folder(self, folder_path):
        """
        Procesa todos los archivos .wav en una carpeta.
        
        Args:
            folder_path (str): Ruta a la carpeta con archivos de audio
        """
        if not os.path.exists(folder_path):
            logger.error(f"La carpeta no existe: {folder_path}")
            return
        
        wav_files = [f for f in os.listdir(folder_path) if f.lower().endswith('.wav')]
        
        if not wav_files:
            logger.warning(f"No se encontraron archivos .wav en: {folder_path}")
            return
        
        logger.info(f"Procesando {len(wav_files)} archivos en {folder_path}")
        
        for filename in wav_files:
            file_path = os.path.join(folder_path, filename)
            if AudioFileHandler(self).wait_for_file_completion(file_path, timeout=60):
                self.process_audio_file(file_path)
            else:
                logger.error(f"No se pudo procesar (archivo incompleto): {file_path}")

class AudioFileHandler(FileSystemEventHandler):
    """
    Handler para monitorear cambios en la carpeta de audio.
    
    INTEGRACIÓN RASPBERRY PI: Esta clase es crucial para el funcionamiento
    en tiempo real con la Raspberry Pi. Monitoreará la carpeta donde el
    programa C++ deposita los archivos de audio grabados.
    """
    
    def __init__(self, processor):
        self.processor = processor
    
    def on_created(self, event):
        """
        Se ejecuta cuando se crea un nuevo archivo .wav
        """
        if not event.is_directory and event.src_path.lower().endswith('.wav'):
            logger.info(f"Nuevo archivo detectado: {event.src_path}")

            # Esperar a que el archivo esté completo antes de procesarlo
            if self.wait_for_file_completion(event.src_path, timeout=360):
                self.processor.process_audio_file(event.src_path)
            else:
                logger.error(f"No se pudo procesar (archivo incompleto): {event.src_path}")

    
    # Verificación de completitud del archivo
    def wait_for_file_completion(self, file_path: str, timeout: int = 360):
        """
        Espera hasta que el archivo esté completamente escrito.

        Útil para asegurar que el programa C++ haya terminado 
        de escribir el archivo antes de procesarlo.

        Args:
            file_path (str): Ruta al archivo
            timeout (int): Tiempo máximo de espera en segundos (default 6 minutos)

        Returns:
            bool: True si el archivo está completo, False si se alcanzó el timeout
        """
        start_time = time.time()
        last_size = -1

        while time.time() - start_time < timeout:
            try:
                current_size = os.path.getsize(file_path)
                if current_size == last_size and current_size > 0:
                    # El archivo dejó de crecer
                    logger.info(f"Archivo completo: {file_path}")
                    time.sleep(1)  # pequeña espera adicional de seguridad
                    return True
                last_size = current_size
                time.sleep(1)  # revisar cada segundo
            except OSError:
                # Puede que el archivo aún no exista del todo
                time.sleep(1)

        logger.warning(f"Timeout esperando completitud del archivo: {file_path}")
        return False

def main():
    """Función principal del script."""
    parser = argparse.ArgumentParser(description='Procesador de audio para monitoreo de ruido')
    parser.add_argument('--watch', action='store_true', 
                       help='Modo de monitoreo continuo (para Raspberry Pi)')
    parser.add_argument('--folder',default="C:/Users/ud/Documents",
    help='Carpeta a monitorear (por defecto: ./audio_files/)'
)

    parser.add_argument('--db', default='noise_data.db',
                       help='Ruta a la base de datos SQLite')
    
    args = parser.parse_args()
    
    # INTEGRACIÓN RASPBERRY PI: En la implementación final, la carpeta debería
    # ser la misma donde el programa C++ deposita los archivos grabados
    # Ejemplo: --folder /home/pi/audio_recordings/
    
    # Crear la carpeta de audio si no existe
    os.makedirs(args.folder, exist_ok=True)
    
    # Inicializar el procesador
    processor = AudioProcessor(db_path=args.db)
    
    if args.watch:
        # Modo de monitoreo continuo (para Raspberry Pi)
        logger.info(f"Iniciando monitoreo continuo de: {args.folder}")
        logger.info("MODO RASPBERRY PI: Esperando archivos del programa C++ de grabación...")
        
        event_handler = AudioFileHandler(processor)
        observer = Observer()
        observer.schedule(event_handler, args.folder, recursive=False)
        observer.start()
        
        try:
            while True:
                time.sleep(1)
                
        except KeyboardInterrupt:
            observer.stop()
            logger.info("Monitoreo detenido por el usuario")
        
        observer.join()
        
    else:
        # Modo de procesamiento por lotes (para desarrollo local)
        logger.info(f"Procesando archivos existentes en: {args.folder}")
        processor.process_folder(args.folder)
        logger.info("Procesamiento por lotes completado")

if __name__ == "__main__":
    main()
