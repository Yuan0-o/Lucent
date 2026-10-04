package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext

internal fun harnessTestContext(): PlatformContext {
    newDesktopContext()?.let { return it }
    allocatedForTest(PlatformContext::class.java)?.let { return it as PlatformContext }
    reflectiveForTest("android.app.Application")?.let { return it as PlatformContext }
    throw IllegalStateException("This platform gives tests no way to make a Context")
}

private fun newDesktopContext(): PlatformContext? = try {
    val cls = Class.forName("com.lucent.app.platform.DesktopPlatformContext")
    cls.getDeclaredConstructor().newInstance() as PlatformContext
} catch (t: Throwable) {
    null
}

internal fun allocatedForTest(type: Class<*>): Any? =
    unsafeAllocate(type) ?: serializationAllocate(type)

private fun reflectiveForTest(name: String): Any? = try {
    Class.forName(name).getDeclaredConstructor().newInstance()
} catch (t: Throwable) {
    null
}

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
