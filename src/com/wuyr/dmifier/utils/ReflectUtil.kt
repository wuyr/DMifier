@file:Suppress("UNCHECKED_CAST", "KDocMissingDocumentation", "PublicApiImplicitType", "unused")

package com.wuyr.dmifier.utils

import com.jetbrains.rd.util.string.println
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-10 上午11:32
 */
const val TAG = "ReflectUtil"

/**
 * 发生异常是否抛出（默认不抛出，只打印堆栈信息）
 */
var throwReflectException: Boolean = true

/**
 * 给对象成员变量设置新的值（可以修改final属性，静态的基本类型除外）
 *
 * @param target 目标对象
 * @param fieldName 目标变量名
 * @param value 新的值
 *
 * @return true为成功
 */
fun Class<*>.set(target: Any?, fieldName: String, value: Any?) = try {
    findField(fieldName).apply {
        isAccessible = true
        if (isLocked()) unlock()
        set(target, value)
    }
    true
} catch (e: Exception) {
    if (throwReflectException) throw e else false
}

private fun Field.isLocked() = modifiers and Modifier.FINAL != 0

private fun Field.unlock() = let { target ->
    try {
        Field::class.java.getDeclaredField("modifiers")
    } catch (e: Exception) {
        Field::class.java.getDeclaredField("accessFlags")
    }.run {
        isAccessible = true
        setInt(target, target.modifiers and Modifier.FINAL.inv())
    }
}

/**
 * 获取目标对象的变量值
 *
 * @param target 目标对象
 * @param fieldName 目标变量名
 *
 * @return 目标变量值（获取失败则返回null）
 */
fun <T> Class<*>.get(target: Any?, fieldName: String) = try {
    findField(fieldName).run {
        isAccessible = true
        get(target) as? T?
    }
} catch (e: Exception) {
    if (throwReflectException) throw e else e.stackTraceString.println()
    null
}

/**
 * 调用目标对象的方法
 *
 * @param target 目标对象
 * @param methodName 目标方法名
 * @param paramsPairs 参数类型和参数值的键值对。示例：
 * <pre>
 *  val view = LayoutInflater::class.invoke<View>(layoutInflater, "tryInflatePrecompiled",
 *      Int::class to R.layout.view_test,
 *      Resource::class to context.resource,
 *      ViewGroup::class to rootView,
 *      Boolean::class to false
 *  )
 * </pre>
 *
 * @return 方法返回值
 */
fun <T> Class<*>.invoke(target: Any?, methodName: String, vararg paramsPairs: Pair<KClass<*>, Any?> = emptyArray()) = try {
    findMethod(methodName, *paramsPairs.map { it.first.java }.toTypedArray()).run {
        isAccessible = true
        invoke(target, *paramsPairs.map { it.second }.toTypedArray()) as? T?
    }
} catch (e: Exception) {
    if (throwReflectException) throw e else e.stackTraceString.println()
    null
}

/**
 * 同上，此乃调用void方法，即无返回值
 */
fun Class<*>.invokeVoid(target: Any?, methodName: String, vararg paramsPairs: Pair<KClass<*>, Any?> = emptyArray()) {
    try {
        findMethod(methodName, *paramsPairs.map { it.first.java }.toTypedArray()).run {
            isAccessible = true
            invoke(target, *paramsPairs.map { it.second }.toTypedArray())
        }
    } catch (e: Exception) {
        if (throwReflectException) throw e else e.stackTraceString.println()
    }
}

/**
 * 创建目标类对象
 *
 * @param paramsPairs 参数类型和参数值的键值对。示例：
 * <pre>
 *  val context = ContextImpl::class.newInstance<Context>(
 *      ActivityThread::class to ...,
 *      LoadedApk::class to ...,
 *      String::class to ...,
 *      IBinder::class to ...,
 *  )
 *
 *  @return 目标对象新实例
 */
fun <T> Class<*>.newInstance(vararg paramsPairs: Pair<KClass<*>, Any?> = emptyArray()) = try {
    getDeclaredConstructor(*paramsPairs.map { it.first.java }.toTypedArray()).run {
        isAccessible = true
        newInstance(*paramsPairs.map { it.second }.toTypedArray()) as? T?
    }
} catch (e: Exception) {
    if (throwReflectException) throw e else e.stackTraceString.println()
    null
}

fun Class<*>.findField(fieldName: String): Field {
    declaredFields.forEach {
        if (it.name == fieldName) {
            return it
        }
    }
    if (this == Any::class.java) {
        throw NoSuchFieldException(fieldName)
    } else {
        if (isInterface) {
            interfaces.forEach {
                runCatching { it.findField(fieldName) }.getOrNull()?.run { return this }
            }
            throw NoSuchFieldException(fieldName)
        } else return superclass.findField(fieldName)
    }
}

fun Class<*>.findMethod(methodName: String, parameterTypes: Array<Class<*>> = emptyArray()): Method {
    declaredMethods.forEach {
        if (it.name == methodName && parameterTypes.contentEquals(it.parameterTypes)) {
            return it
        }
    }
    if (this == Any::class.java) {
        throw NoSuchMethodException(parameterTypes.joinToString(prefix = "$methodName(", postfix = ")") { it.name })
    } else {
        if (isInterface) {
            interfaces.forEach {
                runCatching { it.findMethod(methodName, parameterTypes) }.getOrNull()?.run { return this }
            }
            throw NoSuchMethodException(parameterTypes.joinToString(prefix = "$methodName(", postfix = ")") { it.name })
        } else return superclass.findMethod(methodName, parameterTypes)
    }
}

fun <T> KClass<*>.invoke(target: Any?, methodName: String, vararg paramsPairs: Pair<KClass<*>, Any?> = emptyArray()) = try {
    java.run {
        findMethod(methodName, *paramsPairs.map { it.first.java }.toTypedArray()).run {
            isAccessible = true
            invoke(target, *paramsPairs.map { it.second }.toTypedArray()) as? T?
        }
    }
} catch (e: Exception) {
    if (throwReflectException) throw e else e.println()
    null
}

fun KClass<*>.invokeVoid(target: Any?, methodName: String, vararg paramsPairs: Pair<KClass<*>, Any?> = emptyArray()) {
    try {
        java.run {
            findMethod(methodName, *paramsPairs.map { it.first.java }.toTypedArray()).run {
                isAccessible = true
                invoke(target, *paramsPairs.map { it.second }.toTypedArray())
            }
        }
    } catch (e: Exception) {
        if (throwReflectException) throw e else e.stackTraceString.println()
    }
}

fun <T> KClass<*>.newInstance(vararg paramsPairs: Pair<KClass<*>, Any?> = emptyArray()) = try {
    java.run {
        getDeclaredConstructor(*paramsPairs.map { it.first.java }.toTypedArray()).run {
            isAccessible = true
            newInstance(*paramsPairs.map { it.second }.toTypedArray()) as? T?
        }
    }
} catch (e: Exception) {
    if (throwReflectException) throw e else e.stackTraceString.println()
    null
}

fun String.set(target: Any?, fieldName: String, value: Any?) =
    Class.forName(this).set(target, fieldName, value)

fun <T> String.get(target: Any?, fieldName: String) =
    Class.forName(this).get<T>(target, fieldName)

fun <T> String.invoke(target: Any?, methodName: String, vararg paramsPairs: Pair<KClass<*>, Any?>) =
    Class.forName(this).invoke<T>(target, methodName, *paramsPairs)

fun String.invokeVoid(target: Any?, methodName: String, vararg paramsPairs: Pair<KClass<*>, Any?>) =
    Class.forName(this).invokeVoid(target, methodName, *paramsPairs)

fun <T> String.newInstance(vararg paramsPairs: Pair<KClass<*>, Any?>) =
    Class.forName(this).newInstance<T>(*paramsPairs)

fun KClass<*>.set(target: Any?, fieldName: String, value: Any?) = java.set(target, fieldName, value)

fun <T> KClass<*>.get(target: Any?, fieldName: String) = java.get<T>(target, fieldName)

val Throwable.stackTraceString: String
    get() = StringWriter().use { sw ->
        PrintWriter(sw).use { pw ->
            printStackTrace(pw)
            pw.flush()
        }
        sw.flush()
    }.toString()

fun Any?.println() = println(toString())