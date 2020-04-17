package com.epam.drill.e2e.plugin

import com.epam.drill.e2e.*
import com.epam.drill.plugin.api.processing.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.*
import org.apache.bcel.classfile.*
import org.apache.bcel.generic.*
import java.util.jar.*

@Suppress("UNCHECKED_CAST")
fun MemoryClassLoader.clazz(
    pluginId: String,
    suffix: String,
    entries: Set<JarEntry>,
    jarFile: JarFile
): Class<AgentPart<*, *>> = entries.asSequence().filter {
    it.name.endsWith(".class") && !it.name.contains("module-info")
}.map { jarEntry ->
    jarEntry.name.removeSuffix(".class") to jarFile.getInputStream(jarEntry).use { inStream ->
        ClassParser(inStream, "").parse()
    }
}.onEach { (coreName, javaClass) ->
    val regeneratedClass = ClassGen(javaClass)
    if (javaClass.superclassName == AgentPart::class.qualifiedName) {
        javaClass.className = "${javaClass.className}$suffix"
        val pluginPackage = "com/epam/drill/plugins/$pluginId"
        regeneratedClass.constantPool.constantPool.constantPool.forEachIndexed { idx, constant ->
            if (constant is ConstantUtf8 && "$coreName$" !in constant.bytes) {
                regeneratedClass.constantPool.setConstant(
                    idx,
                    ConstantUtf8(
                        constant.bytes
                            .replace(coreName, "$coreName$suffix")
                            .replace( //TODO this is needed only for test2code plugin, get rid of this
                                "$pluginPackage/DrillProbeArrayProvider",
                                "$pluginPackage/DrillProbeArrayProvider$suffix"
                            )
                    )
                )
            }
        }
        regeneratedClass.constantPool
        regeneratedClass.update()
    } else if (javaClass.className == "com.epam.drill.plugins.$pluginId.DrillProbeArrayProvider") {
        //TODO this is needed only for test2code plugin, get rid of this
        javaClass.className = "${javaClass.className}$suffix"
        regeneratedClass.constantPool.constantPool.constantPool.forEachIndexed { idx, constant ->
            if (constant is ConstantUtf8 && "$coreName$" !in constant.bytes) {
                regeneratedClass.constantPool.setConstant(
                    idx,
                    ConstantUtf8(constant.bytes.replace(coreName, "$coreName$suffix"))
                )
            }
        }
        regeneratedClass.constantPool
        regeneratedClass.update()
    }
    addMainDefinition(javaClass.className, regeneratedClass.javaClass.bytes)
}.map { it.second }
    .last { it.superclassName == "com.epam.drill.plugin.api.processing.AgentPart" }
    .let { loadClass(it.className) as Class<AgentPart<*, *>> }

class OutsSock(private val mainChannel: SendChannel<Frame>, private val withDebug: Boolean = false) :
    SendChannel<Frame> by mainChannel {
    override suspend fun send(element: Frame) {
        if (withDebug && element is Frame.Text) {
            println("AGENT OUT: ${element.readText()}")
        }
        mainChannel.send(element)
    }
}
