package com.lucent.app.harness

import android.content.Context

internal fun harnessTestContext(): Context = allocateForTest(Context::class.java) as Context

internal fun allocateForTest(type: Class<*>): Any? = unsafeAllocate(type) ?: serializationAllocate(type)

private fun unsafeAllocate(type: Class<*>): Any? = try {
    val unsafe = Class.forName("sun.misc.Unsafe")
    val field = unsafe.getDeclaredField("theUnsafe")
    field.isAccessible = true
    unsafe.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), type)
} catch (t: Throwable) {
    null
}

private fun serializationAllocate(type: Class<*>): Any? = try {
    val factoryType = Class.forName("sun.reflect.ReflectionFactory")
    val factory = factoryType.getMethod("getReflectionFactory").invoke(null)
    val marker = Any::class.java.getDeclaredConstructor()
    val creator = factoryType.getMethod(
        "newConstructorForSerialization",
        Class::class.java,
        java.lang.reflect.Constructor::class.java
    ).invoke(factory, type, marker) as java.lang.reflect.Constructor<*>
    creator.isAccessible = true
    creator.newInstance()
} catch (t: Throwable) {
    null
}
