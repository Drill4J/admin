/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code.util

import com.epam.dsm.util.*

fun fullClassname(path: String, className: String): String = "$path/$className".weakIntern()

fun signature(
    clazz: String,
    method: String,
    desc: String
): String = "${methodName(method, clazz)}$desc".weakIntern()

fun fullMethodName(
    clazz: String,
    method: String,
    desc: String
): String = "$clazz.${signature(clazz, method, desc)}".weakIntern()

fun classPath(clazz: String) = clazz.substringBeforeLast("/").weakIntern()

fun methodName(method: String, fullClassname: String): String = when (method) {
    "<init>" -> classname(fullClassname)
    "<clinit>" -> "static ${classname(fullClassname)}".weakIntern()
    else -> method.weakIntern()
}

fun classname(
    fullClassname: String
): String = fullClassname.substringAfterLast('/').weakIntern()
