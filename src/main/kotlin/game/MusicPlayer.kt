package game

import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class BackgroundMusic(resourcePath: String, private val loop: Boolean = true) {
    private val clip: Clip

    init {
        val stream = BufferedInputStream(
            requireNotNull(javaClass.getResourceAsStream(resourcePath)) {
                "Resource not found: $resourcePath"
            }
        )
        val audioIn = AudioSystem.getAudioInputStream(stream)
        clip = AudioSystem.getClip().apply { open(audioIn) }
    }

    fun play() {
        if (loop) clip.loop(Clip.LOOP_CONTINUOUSLY)
        clip.start()
    }

    fun pause() = clip.stop()

}
