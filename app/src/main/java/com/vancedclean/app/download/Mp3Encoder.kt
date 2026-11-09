package com.vancedclean.app.download

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.OutputStream

/**
 * Codificador MP3 (LAME portado a Java, librería jump3r). Se usa directamente el núcleo
 * porque su clase "LameEncoder" depende de javax.sound, que no existe en Android.
 * Recibe PCM de 16 bits intercalado y escribe MP3 CBR al [out].
 */
class Mp3Encoder(
    private val channels: Int,
    sampleRate: Int,
    bitrateKbps: Int,
    private val out: OutputStream,
) {
    private val lame = Lame()
    private val gfp: LameGlobalFlags
    private val mp3Buf = ByteArray(1024 * 1024)
    private var left = IntArray(0)
    private var right = IntArray(0)

    init {
        val ga = GainAnalysis()
        val bs = BitStream()
        val presets = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()
        val gaud = GetAudio()

        lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        presets.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, presets)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        gfp = lame.lame_init()
        gfp.num_channels = channels.coerceIn(1, 2)
        gfp.in_samplerate = sampleRate
        gfp.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        gfp.VBR = VbrMode.vbr_off
        gfp.brate = bitrateKbps
        gfp.quality = 5 // buen equilibrio entre calidad y velocidad en el teléfono
        gfp.bWriteVbrTag = false
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false
        check(lame.lame_init_params(gfp) >= 0) { "No se pudo iniciar el codificador MP3" }
    }

    /** [pcm] son muestras de 16 bits intercaladas (L, R, L, R...) */
    fun encode(pcm: ShortArray, sampleCount: Int) {
        val frames = sampleCount / channels
        if (frames == 0) return
        if (left.size < frames) {
            left = IntArray(frames)
            right = IntArray(frames)
        }
        if (channels == 1) {
            for (i in 0 until frames) {
                val v = pcm[i].toInt() shl 16
                left[i] = v
                right[i] = v
            }
        } else {
            for (i in 0 until frames) {
                left[i] = pcm[i * channels].toInt() shl 16
                right[i] = pcm[i * channels + 1].toInt() shl 16
            }
        }
        val n = lame.lame_encode_buffer_int(gfp, left, right, frames, mp3Buf, 0, mp3Buf.size)
        check(n >= 0) { "Error codificando MP3 ($n)" }
        if (n > 0) out.write(mp3Buf, 0, n)
    }

    fun finish() {
        val n = lame.lame_encode_flush(gfp, mp3Buf, 0, mp3Buf.size)
        if (n > 0) out.write(mp3Buf, 0, n)
        lame.lame_close(gfp)
    }
}
