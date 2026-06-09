package fr.neiyo.resonance.core.audio.type;

import fr.neiyo.resonance.core.audio.AudioSource;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Mp3AudioSource implements AudioSource {

    private static final int TARGET_SAMPLE_RATE = 48000;
    private static final int FRAME_SIZE = 960;

    private final Path filePath;
    private short[] pcmData;
    private int position;

    public Mp3AudioSource(Path filePath) {
        this.filePath = filePath;
        this.position = 0;
        decodeAndConvert();
    }

    private void decodeAndConvert() {
        try (InputStream fis = new BufferedInputStream(new FileInputStream(filePath.toFile()))) {
            Bitstream bitstream = new Bitstream(fis);
            Decoder decoder = new Decoder();

            List<short[]> frames = new ArrayList<>();
            int totalSamples = 0;
            int sourceSampleRate = -1;
            int sourceChannels = -1;

            Header frameHeader;
            while ((frameHeader = bitstream.readFrame()) != null) {
                if (sourceSampleRate == -1) {
                    sourceSampleRate = frameHeader.frequency();
                    sourceChannels = (frameHeader.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                }

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
                short[] buffer = output.getBuffer();
                int bufferLength = output.getBufferLength();

                short[] frameCopy = new short[bufferLength];
                System.arraycopy(buffer, 0, frameCopy, 0, bufferLength);
                frames.add(frameCopy);
                totalSamples += bufferLength;

                bitstream.closeFrame();
            }

            bitstream.close();

            short[] rawPcm = new short[totalSamples];
            int offset = 0;
            for (short[] frame : frames) {
                System.arraycopy(frame, 0, rawPcm, offset, frame.length);
                offset += frame.length;
            }

            short[] monoPcm;
            if (sourceChannels == 2) {
                monoPcm = stereoToMono(rawPcm);
            } else {
                monoPcm = rawPcm;
            }

            if (sourceSampleRate != TARGET_SAMPLE_RATE) {
                pcmData = resample(monoPcm, sourceSampleRate, TARGET_SAMPLE_RATE);
            } else {
                pcmData = monoPcm;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to decode MP3 file: " + filePath, e);
        }
    }

    private short[] stereoToMono(short[] stereo) {
        int monoLength = stereo.length / 2;
        short[] mono = new short[monoLength];
        for (int i = 0; i < monoLength; i++) {
            int left = stereo[i * 2];
            int right = stereo[i * 2 + 1];
            mono[i] = (short) ((left + right) / 2);
        }
        return mono;
    }

    private short[] resample(short[] input, int fromRate, int toRate) {
        double ratio = (double) toRate / fromRate;
        int outputLength = (int) (input.length * ratio);
        short[] output = new short[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double srcPos = i / ratio;
            int srcIndex = (int) srcPos;
            double fraction = srcPos - srcIndex;

            if (srcIndex + 1 < input.length) {
                output[i] = (short) (input[srcIndex] * (1.0 - fraction) + input[srcIndex + 1] * fraction);
            } else if (srcIndex < input.length) {
                output[i] = input[srcIndex];
            }
        }

        return output;
    }

    @Override
    @Nullable
    public short[] nextFrame() {
        if (!hasNext()) return null;

        short[] frame = new short[FRAME_SIZE];
        int samplesAvailable = Math.min(FRAME_SIZE, pcmData.length - position);
        System.arraycopy(pcmData, position, frame, 0, samplesAvailable);

        position += FRAME_SIZE;
        return frame;
    }

    @Override
    public void reset() {
        position = 0;
    }

    @Override
    public void close() {
        pcmData = null;
    }

    @Override
    public boolean hasNext() {
        return pcmData != null && position < pcmData.length;
    }
}