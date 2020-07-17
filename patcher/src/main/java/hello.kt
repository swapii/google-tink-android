import kotlin.IllegalArgumentException
import java.io.File
import java.lang.System
import java.util.UUID
import javassist.ClassPool
import javassist.CtClass
import javassist.Modifier
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

fun main(args: Array<String>) {

    require(args.size == 2) { "You should pass only two arguments (source file and target file)" }

    val sourceFile = File(args[0])
    require(sourceFile.exists()) { "Source file [${sourceFile.absolutePath}] should exist in path" }

    val targetFile = File(args[1])
    require(!targetFile.exists()) { "Target file [${targetFile.absolutePath}] should not be exist" }

    sourceFile.copyTo(targetFile)

    patchJar(targetFile)
}

private fun patchJar(outputFile: File) {

    val jarFile = ZipFile(outputFile)

    val classPool = ClassPool.getDefault()

    classPool.appendSystemPath()
    classPool.appendClassPath(getAndroidJarFile().absolutePath)
    classPool.appendClassPath(outputFile.absolutePath)

    jarFile.modifyClass(
        classPool,
        "com.google.crypto.tink.KeyTypeManager\$PrimitiveFactory"
    ) {
        modifiers = Modifier.PUBLIC
    }

    jarFile.modifyClass(
        classPool,
        "com.google.crypto.tink.subtle.AesGcmJce"
    ) {
        declaredMethods
            .single { it.name == "getParams" && it.parameterTypes.size == 3 }
            .setBody(
                """{
                        
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
                            return new javax.crypto.spec.IvParameterSpec($1, $2, $3);
                        }
                        
                        try {
                            Class.forName("javax.crypto.spec.GCMParameterSpec");
                        } catch (java.lang.ClassNotFoundException e) {
                            if (com.google.crypto.tink.subtle.SubtleUtil.isAndroid()) {
                                return new javax.crypto.spec.IvParameterSpec($1, $2, $3);
                            }
                            throw new java.security.GeneralSecurityException(
                                "cannot use AES-GCM: javax.crypto.spec.GCMParameterSpec not found"
                            );
                        }
                        
                        return new javax.crypto.spec.GCMParameterSpec(8 * TAG_SIZE_IN_BYTES, $1, $2, $3);
                        
                    }""".trimIndent()
            )
    }
}

private fun getAndroidJarFile(): File {
    val androidHomeEnv = System.getenv()["ANDROID_HOME"]
    require(!androidHomeEnv.isNullOrBlank()) { "Can't find environment variable ANDROID_HOME" }
    return File("$androidHomeEnv/platforms/android-30/android.jar")
}

private fun ZipFile.modifyClass(
    classPool: ClassPool,
    targetClassName: QualifiedClassName,
    modifyClass: CtClass.() -> Unit
) {

    val targetClassFilePath = targetClassName.getFilePath()

    val targetClassFileHeader = getFileHeader(targetClassFilePath)
        ?: error("Can't find target class file [$targetClassFilePath]")

    val targetClassControl = getInputStream(targetClassFileHeader)
        .use { classPool.makeClass(it) }

    targetClassControl.modifyClass()

    val targetClassBytecode = targetClassControl.toBytecode()

    replaceFile(targetClassFilePath, targetClassBytecode)

}

private fun ZipFile.replaceFile(
    fileName: String,
    targetClassBytecode: ByteArray
) {

    removeFile(fileName)

    addFile(
        File.createTempFile(UUID.randomUUID().toString(), null)
            .apply {
                deleteOnExit()
                writeBytes(targetClassBytecode)
            },
        ZipParameters().apply {
            fileNameInZip = fileName
        }
    )

}

typealias QualifiedClassName = String

fun QualifiedClassName.getFilePath(): String = replace('.', '/') + ".class"
