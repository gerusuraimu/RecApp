package com.example.recapp

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var outputFile: File
    private lateinit var recordButton: Button

    private var isRecording: Boolean = false
    private val requestRecordPermission: Int = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestAudioPermissions()

        recordButton = findViewById(R.id.button)
        recordButton.setOnClickListener {
            if (isRecording){
                stopRecording()
            } else {
                startRecording()
            }
        }

    }

    private fun requestAudioPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.RECORD_AUDIO),
            requestRecordPermission
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestRecordPermission){
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(
                    this,
                    "Permissions granted!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Permissions denied!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermissions()
        }

        outputFile = File(externalCacheDir?.absolutePath, "recording.pcm")

        val sampleRate = 48000
        val channelConfig: Int = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord.startRecording()
        isRecording = true
        recordButton.text = "Stop Recording"

        Thread {
            val audioData = ByteArray(bufferSize)

            try {
                val fileOutputStream = FileOutputStream(outputFile)

                while (isRecording) {
                    val bytesRead: Int = audioRecord.read(audioData, 0, audioData.size)
                    if (bytesRead > 0) {
                        fileOutputStream.write(audioData, 0, bytesRead)
                    }
                }

                fileOutputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            audioRecord.stop()
            audioRecord.release()
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun stopRecording() {
        isRecording = false
        recordButton.text = "Start Recording"

        val wavOutputFile = File(externalCacheDir?.absolutePath, "recording.pcm")
        convertPcmToWav(outputFile, wavOutputFile)
    }

    private fun convertPcmToWav(pcmFile: File, wavFile: File) {
        val header: ByteArray = createWavHeader(pcmFile)

        try {
            val inputStream: FileInputStream = pcmFile.inputStream()
            val outputStream: FileOutputStream = wavFile.outputStream()
            outputStream.write(header)

            val data = ByteArray(4096)
            var bytesRead: Int

            while (inputStream.read(data).also { bytesRead = it } != -1) {
                outputStream.write(data, 0, bytesRead)
            }

            inputStream.close()
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun createWavHeader(pcmFile: File): ByteArray {
        val sampleRate = 48000
        val channels = 1
        val bitsPerSample = 16

        val byteRate: Int = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign: Int = channels + (bitsPerSample / 8)
        val dataSize: Int = pcmFile.length().toInt()
        val totalSize: Int = dataSize + 36

        val header: ByteBuffer = ByteBuffer.allocate(44)

        header.order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        return header.array()
    }
}