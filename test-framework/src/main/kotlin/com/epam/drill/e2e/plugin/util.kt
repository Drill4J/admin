package com.epam.drill.e2e.plugin

import com.epam.drill.e2e.*
import com.epam.drill.plugin.api.processing.*
import org.apache.bcel.classfile.*
import org.apache.bcel.generic.*
import java.util.jar.*

@Suppress("UNCHECKED_CAST")
fun MemoryClassLoader.clazz(
    classSuffix: String,
    entries: Set<JarEntry>,
    jarFile: JarFile
): Class<AgentPart<*, *>> {
    return this.loadClass(
        entries
            .filter { it.name.endsWith(".class") && !it.name.contains("module-info") }
            .map {
                val javaClass = jarFile.getInputStream(it).use { istream -> ClassParser(istream, "").parse() }
                val coreName = it.name.removeSuffix(".class")
                val regeneratedClass = ClassGen(javaClass)
                if (javaClass.superclassName == "com.epam.drill.plugin.api.processing.AgentPart") {
                    javaClass.className = "${javaClass.className}$classSuffix"
                    regeneratedClass.constantPool.constantPool.constantPool.forEachIndexed { idx, constant ->
                        if (constant is ConstantUtf8) {
                            if (!constant.bytes.contains("$coreName$"))
                                regeneratedClass.constantPool.setConstant(
                                    idx,
                                    ConstantUtf8(constant.bytes.replace(coreName, "$coreName$classSuffix"))
                                )
                        }
                    }
                    regeneratedClass.constantPool
                    regeneratedClass.update()
                }
                this.addMainDefinition(javaClass.className, regeneratedClass.javaClass.bytes)
                javaClass
            }.find { it.superclassName == "com.epam.drill.plugin.api.processing.AgentPart" }!!.className
    ) as Class<AgentPart<*, *>>
}