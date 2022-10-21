package org.mider.produce.bot

import io.github.mzdluo123.silk4j.AudioUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.events.BotEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.info
import org.mider.produce.bot.game.gameStart
import org.mider.produce.bot.utils.matchRegex
import org.mider.produce.core.Configuration
import org.mider.produce.core.initTmpAndFormatTransfer
import org.mider.produce.core.utils.toPinyin
import whiter.music.mider.code.MacroConfiguration
import whiter.music.mider.code.MacroConfigurationBuilder
import whiter.music.mider.code.MiderCodeParserConfiguration
import whiter.music.mider.xml.LyricInception
import java.net.URL

object MiderBot : KotlinPlugin(
    JvmPluginDescription(
        id = "org.mider.produce.bot",
        name = "MiderBot",
        version = "0.1.8",
    ) {
        author("whiterasbk")
    }
) {
    // todo 1. 出场自带 bgm ( 频率
    // todo 2. 相对音准小测试
    // todo 3. 隔群发送语音, 寻觅知音 (
    // todo 4. 增加乐器( done
    // todo 5. 增加力度 done
    // todo 6. mider code for js
    // todo 7. 权限系统, 话说就发个语音有引入命令权限的必要吗 (
    // todo 8. midi 转 mider code

    val cache = mutableMapOf<String, Message>()
    private val tmpDir = resolveDataFile("tmp")
    private lateinit var macroConfig: MacroConfiguration
    // TODO MiderCodeParserConfiguration.Buider该改改，允许直接 setMacroConfiguration
    val produceCoreConfiguration = MiderCodeParserConfiguration()

    override fun onEnable() {
        logger.info { "MidiProduce loaded" }

        BotConfiguration.reload()

        val cfg = Configuration(tmpDir)
        cfg.resolveFileAction = ::resolveDataFile

        cfg.initTmpAndFormatTransfer(this)

        cfg.info = {
            logger.info(it.toString())
        }

        cfg.error = {
            if (it is Throwable)
                logger.error(it)
            else logger.error(it.toString())
        }

        BotConfiguration.copy(cfg)

        LyricInception.replace = { it.toPinyin() }

        macroConfig = MacroConfigurationBuilder()
            .recursionLimit(cfg.recursionLimit)
            .loggerError { if (cfg.macroUseStrictMode) throw it else this@MiderBot.logger.error(it) }
            .fetchMethod {
                if (it.startsWith("http://") || it.startsWith("https://") || it.startsWith("ftp://"))
                    URL(it).openStream().reader().readText()
                else
                    resolveDataFile(it.replace("file:", "")).readText()
            }
            .build()

        produceCoreConfiguration.macroConfiguration = macroConfig
        produceCoreConfiguration.isBlankReplaceWith0 = BotConfiguration.isBlankReplaceWith0

        val process: suspend MessageEvent.() -> Unit = {
            var finishFlag = false
            // todo 改为有概率触发
            if (BotConfiguration.selfMockery && Math.random() > 0.5) launch {
                delay(BotConfiguration.selfMockeryTime)
                // 开始嘲讽
                if (!finishFlag) {
                    subject.sendMessage("...")
                    delay(50)
                    val tty = resolveDataFile("2000-years-later.png")
                    if (tty.exists()) {
                        tty.toExternalResource().use {
                            subject.sendMessage(subject.uploadImage(it))
                        }
                    } else subject.sendMessage("10 years later.")
                }
            }

            try {
                handle(cfg, produceCoreConfiguration)
                oCommandProcess()
            } catch (e: Exception) {
                logger.error(e)
                subject.sendMessage("发生错误>类型:${e::class.simpleName}>" + e.message)
            } finally {
                finishFlag = true
            }
        }

        val botEvent = globalEventChannel().filter { it is BotEvent }

        botEvent.subscribeAlways<GroupMessageEvent>{
            process()
        }

        botEvent.subscribeAlways<FriendMessageEvent> {
            process()
        }
    }

    private suspend fun MessageEvent.oCommandProcess() {
        val oCmdRegex = Regex(">!([\\w@=:&%$#\\->]+)>")
        matchRegex(oCmdRegex) {
            val content = oCmdRegex.matchEntire(it)!!.groupValues[1]
            if (content == "help") {
                subject.sendMessage(BotConfiguration.help)
            } else if (content.startsWith("formatMode=")) {
                when (val mode = content.replace("formatMode=", "")) {
                    "internal->java-lame->silk4j", "timidity->ffmpeg", "timidity->ffmpeg->silk4j", "internal->java-lame",
                    "timidity->java-lame", "timidity->java-lame->silk4j", "muse-score", "muse-score->silk4j" -> {
                        val before = BotConfiguration.formatMode
                        BotConfiguration.formatMode = mode
                        if (mode.contains("silk4j")) AudioUtils.init(tmpDir)
                        cache.clear()
                        subject.sendMessage("设置生成模式成功, 由 $before 切换为 $mode")
                    }

                    else -> subject.sendMessage("不支持的模式, 请确认设置的值在以下列表\n" +
                            "internal->java-lame\n" +
                            "internal->java-lame->silk4j,\n" +
                            "timidity->ffmpeg,\n" +
                            "timidity->ffmpeg->silk4j,\n" +
                            "timidity->java-lame,\n" +
                            "timidity->java-lame->silk4j,\n" +
                            "muse-score,\n" +
                            "muse-score->silk4j")
                }
            } else if (content == "clear-cache") {
                cache.clear()
                subject.sendMessage("cache cleared")
            } else if (content.startsWith("game-start:")) {
                gameStart(content.replaceFirst("game-start:", ""))
            }
        }
    }
}

