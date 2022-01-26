package com.wuyr.dmifier.core

import org.objectweb.asm.*
import org.objectweb.asm.util.Printer
import java.lang.reflect.Modifier
import java.util.*

class DMifier : Printer(Opcodes.ASM9) {

    private fun StringBuilder.newLine() = append("\n")

    override fun visit(
        version: Int, access: Int, name: String?, signature: String?, superName: String, interfaces: Array<out String>
    ) {
        appendCodeBlockAndAdd {
            append("TypeId classId = ")
            append(name?.typeId)
            append(";")
            newLine()
            append("String fileName = \"")
            val simpleName = name?.run {
                val lastSlashIndex = lastIndexOf('/')
                if (lastSlashIndex == -1) name
                else substring(lastSlashIndex + 1).replace("[-()]".toRegex(), "_")
            } ?: "DexMakerClass"
            append(simpleName)
            append(".generated\";")
            newLine()
            append("DexMaker dexMaker = new DexMaker();")
            newLine()
            append("TypeId[] interfacesTypes = new TypeId[")
            append(interfaces.size)
            append("];")
            newLine()
            interfaces.forEachIndexed { index, content ->
                append("interfacesTypes[")
                append(index)
                append("] = ")
                append(content.typeId)
                append(";")
                newLine()
            }
            append("dexMaker.declare(classId, fileName, ")
            append((access or ACCESS_CLASS).accessFlag)
            append(", ")
            append(superName.typeId)
            append(", interfacesTypes);")
            newLine()
        }
    }

    override fun visit(name: String?, value: Any?) {
        appendCodeBlockAndAdd {
            append("TypeId classId = ")
            append(name?.typeId)
            append(";")
            newLine()
            append("String fileName = \"")
            val simpleName = name?.run {
                val lastSlashIndex = lastIndexOf('/')
                if (lastSlashIndex == -1) name
                else substring(lastSlashIndex + 1).replace("[-()]".toRegex(), "_")
            } ?: "DexMakerClass"
            append(simpleName)
            append(".generated\";")
            newLine()
            append("DexMaker dexMaker = new DexMaker();")
            newLine()
            append("dexMaker.declare(classId, fileName, ")
            append("Modifier.PUBLIC")
            append(");")
            newLine()
        }
    }

    private val Int.accessFlag: String
        get() = StringBuilder().also { sb ->
            if (this == 0) {
                sb.append("0")
            } else {
                if ((this and Modifier.PUBLIC) != 0) sb.append("Modifier.PUBLIC | ")
                if ((this and Modifier.PRIVATE) != 0) sb.append("Modifier.PRIVATE | ")
                if ((this and Modifier.PROTECTED) != 0) sb.append("Modifier.PROTECTED | ")
                if ((this and Modifier.STATIC) != 0) sb.append("Modifier.STATIC | ")
                if ((this and Modifier.FINAL) != 0) sb.append("Modifier.FINAL | ")
                if ((this and Modifier.SYNCHRONIZED) != 0 && this and ACCESS_CLASS == 0) sb.append("Modifier.SYNCHRONIZED | ")
                if ((this and Modifier.VOLATILE) != 0) sb.append("Modifier.VOLATILE | ")
                if ((this and Modifier.TRANSIENT) != 0 && this and ACCESS_FIELD != 0) sb.append("Modifier.TRANSIENT | ")
                if ((this and Modifier.NATIVE) != 0 && this and (ACCESS_CLASS or ACCESS_FIELD) == 0) sb.append("Modifier.NATIVE | ")
                if ((this and Modifier.INTERFACE) != 0) sb.append("Modifier.INTERFACE | ")
                if ((this and Modifier.ABSTRACT) != 0) sb.append("Modifier.ABSTRACT | ")
                if ((this and Modifier.STRICT) != 0) sb.append("Modifier.STRICT | ")
                if (sb.isEmpty()) {
                    sb.append("0")
                } else {
                    sb.deleteCharAt(sb.lastIndex)
                    sb.deleteCharAt(sb.lastIndex)
                    sb.deleteCharAt(sb.lastIndex)
                }
            }
        }.toString()

    override fun visitSource(source: String?, debug: String?) {

    }

    override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {

    }

    override fun visitClassAnnotation(descriptor: String?, visible: Boolean): Printer {

        return this
    }

    override fun visitClassAttribute(attribute: Attribute?) {

    }

    override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {

    }

    override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): Printer {
        appendCodeBlockAndAdd {
            append("dexMaker.declare(classId.getField(")
            append(descriptor.typeId)
            append(", \"")
            append(name)
            append("\"), ")
            append((access or ACCESS_FIELD).accessFlag)
            append(", ")
            append(value)
            append(");")
        }
        return this
    }

    private var currentMethodParameters = emptyList<String>()
    private var isStaticMethod = false

    override fun visitMethod(
        access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?
    ): Printer {
        appendCodeBlockAndAdd {
            newLine()
            append("{")
            newLine()
            val parameters = descriptor.getParameterTypes()
            isStaticMethod = (access and Modifier.STATIC) != 0
            currentMethodParameters = parameters
            localCount = currentMethodParameters.size + if (isStaticMethod) 0 else 1
            append("MethodId methodId = classId.")
            when (name) {
                "<init>" -> {
                    append("getConstructor(")
                    if (parameters.isNotEmpty()) {
                        parameters.forEach {
                            append(it.typeId)
                            append(", ")
                        }
                        deleteCharAt(lastIndex)
                        deleteCharAt(lastIndex)
                    }
                    append(");")
                }
                "<clinit>" -> {
                    append("getStaticInitializer();")
                }
                else -> {
                    append("getMethod(")
                    append(descriptor.getReturnType().typeId)
                    append(", \"")
                    append(name)
                    if (parameters.isEmpty()) {
                        append("\"")
                    } else {
                        append("\", ")
                        parameters.forEach {
                            append(it.typeId)
                            append(", ")
                        }
                        deleteCharAt(lastIndex)
                        deleteCharAt(lastIndex)
                    }
                    append(");")
                }
            }
            newLine()
            append("Code methodCodeBlock = dexMaker.declare(methodId, ")
            append(access.accessFlag)
            append(");")
            newLine()
        }
        return this
    }

    private fun String.getReturnType() = substring(lastIndexOf(')') + 1)

    private fun String.getParameterTypes() = substring(1, lastIndexOf(')')).run {
        ArrayList<String>().apply {
            split(";").forEach { type ->
                if (type.isNotEmpty()) {
                    if (type.contains('[') && type.indexOf('[') != type.lastIndexOf('[')) {
                        type.split("[").forEach {
                            if (it.isNotEmpty()) {
                                addParameter("[$it")
                            }
                        }
                    } else {
                        addParameter(type)
                    }
                }
            }
            removeIf { it.isEmpty() }
        }
    }

    private fun ArrayList<String>.addParameter(type: String) {
        if (type.startsWith('L') || type.startsWith("[L")) {
            add(type)
        } else {
            val index = type.indexOf(if (type.contains('[')) '[' else 'L')
            if (index == 0 && type.length >= 2) {
                add(type.substring(0, 2))
                add(type.substring(2, type.length))
            } else if (index > -1) {
                type.substring(0, index).forEach { c -> add(c.toString()) }
                add(type.substring(index, type.length))
            } else {
                type.forEach { c -> add(c.toString()) }
            }
        }
    }

    private val String.typeId: String
        get() = when (this) {
            "Z" -> "TypeId.BOOLEAN"
            "C" -> "TypeId.CHAR"
            "F" -> "TypeId.FLOAT"
            "D" -> "TypeId.DOUBLE"
            "B" -> "TypeId.BYTE"
            "S" -> "TypeId.SHORT"
            "I" -> "TypeId.INT"
            "J" -> "TypeId.LONG"
            "V" -> "TypeId.VOID"
            else -> StringBuilder().also { sb ->
                sb.append("TypeId.get(\"")
                if ((!startsWith('L')) && !startsWith('[')) sb.append('L')
                sb.append(this)
                if (!endsWith(';') && length != 2) sb.append(';')
                sb.append("\")")
            }.toString()
        }

    private fun String.getMethodId(name: String, returnType: String, parameterTypes: List<String>) = appendCodeBlock {
        append(typeId)
        append(".getMethod(")
        append(returnType.typeId)
        append(", \"")
        append(name)
        if (parameterTypes.isEmpty()) {
            append("\")")
        } else {
            append("\", ")
            append(parameterTypes.joinToString { it.typeId })
            append(")")
        }
    }

    private fun String.getFieldId(type: String, name: String) = appendCodeBlock {
        append(typeId)
        append(".getField(")
        append(type.typeId)
        append(", \"")
        append(name)
        append("\")")
    }

    override fun visitClassEnd() {
//        appendCodeBlockAndAdd {
//            newLine()
//            append("byte[] codeData = dexMaker.generate();")
//            newLine()
//            append("// write to file")
//            newLine()
//            append("// FIXME: modify output path")
//            newLine()
//            append("File outputFile = null;")
//            newLine()
//            append("FileOutputStream fos = new FileOutputStream(outputFile);")
//            newLine()
//            append("fos.write(codeData);")
//            newLine()
//            append("fos.flush();")
//            newLine()
//            append("fos.close();")
//            newLine()
//            newLine()
//            append("// load directly")
//            newLine()
//            append("// FIXME: complete classLoader and dexFilePath")
//            newLine()
//            append("ClassLoader parentClassLoader = null;")
//            newLine()
//            append("File dexFilePath = null;")
//            newLine()
//            append("ClassLoader loader = dexMaker.generateAndLoad(parentClassLoader, dexFilePath);")
//            newLine()
//            append("Class<?> generatedClass = loader.loadClass(classId.getName().substring(1, classId.getName().length() - 1));")
//            newLine()
//        }
    }

    override fun visitEnum(name: String?, descriptor: String?, value: String?) {

    }

    override fun visitAnnotation(name: String?, descriptor: String?): Printer {

        return this
    }

    override fun visitArray(name: String?): Printer {

        return this
    }

    override fun visitAnnotationEnd() {

    }

    override fun visitFieldAnnotation(descriptor: String?, visible: Boolean): Printer {

        return this
    }

    override fun visitFieldAttribute(attribute: Attribute?) {

    }

    override fun visitFieldEnd() {

    }

    override fun visitAnnotationDefault(): Printer {

        return this
    }

    override fun visitMethodAnnotation(descriptor: String?, visible: Boolean): Printer {
        return this
    }

    override fun visitParameterAnnotation(parameter: Int, descriptor: String?, visible: Boolean): Printer {
        return this
    }

    override fun visitMethodAttribute(attribute: Attribute?) {

    }

    override fun visitCode() {

    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) {

    }

    private var operationStringBuilder = StringBuilder()
    private var declarationStringBuilder = StringBuilder()

    private fun appendDeclarationCodeBlock(block: StringBuilder.() -> Unit) {
        declarationStringBuilder.apply {
            block()
            newLine()
        }
    }

    private fun appendOperationCodeBlock(appendNewLine: Boolean = true, block: StringBuilder.() -> Unit) {
        operationStringBuilder.apply {
            block()
            if (appendNewLine) {
                newLine()
            }
        }
    }

    private fun flushTempCodeBlock() {
        declarationStringBuilder.newLine()
        text.add(declarationStringBuilder.toString())
        text.add(operationStringBuilder.toString())
        declarationStringBuilder.setLength(0)
        operationStringBuilder.setLength(0)
    }

    // localName : typeId
    private val stack = LinkedList<Pair<String, String>>()

    private var localCount = 0
    private var tempLocalCount = 0
    private val localNames = HashMap<String, Pair<String, String>>()

    private fun newLocalName(typeId: String, store: Boolean = false) =
        (if (store) "local${localCount++}" else "tempLocal${tempLocalCount++}").also { name ->
            if (store) {
                if (typeId == "TypeId.LONG" || typeId == "TypeId.DOUBLE") {
                    localCount++
                }
                localNames[name] = name to typeId
            }
        }

    private fun cast(targetType: String) {
        val typeId = targetType.typeId
        val output = newLocalName(typeId)
        appendDeclarationCodeBlock {
            append("Local ")
            append(output)
            append(" = methodCodeBlock.newLocal(")
            append(typeId)
            append(");")
        }
        appendOperationCodeBlock {
            append("methodCodeBlock.cast(")
            append(output)
            append(", ")
            append(stack.pop().first)
            append(");")
        }
        stack.push(output to typeId)
    }

    private fun loadConstant(type: String, value: Any?) {
        val typeId = type.typeId
        val output = newLocalName(typeId)
        appendDeclarationCodeBlock {
            append("Local ")
            append(output)
            append(" = methodCodeBlock.newLocal(")
            append(typeId)
            append(");")
        }
        appendOperationCodeBlock {
            append("methodCodeBlock.loadConstant(")
            append(output)
            append(", ")
            append(value)
            append(");")
        }
        stack.push(output to typeId)
    }

    override fun visitInsn(opcode: Int) {
        when (opcode) {
            Opcodes.ACONST_NULL -> loadConstant("java/lang/Object", null)
            Opcodes.ICONST_M1 -> loadConstant("I", -1)
            Opcodes.ICONST_0 -> loadConstant("I", 0)
            Opcodes.ICONST_1 -> loadConstant("I", 1)
            Opcodes.ICONST_2 -> loadConstant("I", 2)
            Opcodes.ICONST_3 -> loadConstant("I", 3)
            Opcodes.ICONST_4 -> loadConstant("I", 4)
            Opcodes.ICONST_5 -> loadConstant("I", 5)
            Opcodes.LCONST_0 -> loadConstant("J", 0)
            Opcodes.LCONST_1 -> loadConstant("J", 1)
            Opcodes.FCONST_0 -> loadConstant("F", 0)
            Opcodes.FCONST_1 -> loadConstant("F", 1)
            Opcodes.FCONST_2 -> loadConstant("F", 2)
            Opcodes.DCONST_0 -> loadConstant("D", 0)
            Opcodes.DCONST_1 -> loadConstant("D", 1)

            Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD,
            Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> {
                val typeId = stack[1].second.replaceFirst("[", "")
                val output = newLocalName(typeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(")
                    append(typeId)
                    append(");")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.aget(")
                    append(output)
                    append(", ")
                    append(stack[1].first)
                    append(", ")
                    append(stack[0].first)
                    append(");")
                    stack.pop()
                    stack.pop()
                }
                stack.push(output to typeId)
            }

            Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE,
            Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.aput(")
                    append(stack[2].first)
                    append(", ")
                    append(stack[1].first)
                    append(", ")
                    append(stack[0].first)
                    append(");")
                    stack.pop()
                    stack.pop()
                    stack.pop()
                }
            }

            Opcodes.ARRAYLENGTH -> {
                val typeId = "TypeId.INT"
                val output = newLocalName(typeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(")
                    append(typeId)
                    append(");")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.arrayLength(")
                    append(output)
                    append(", ")
                    append(stack.pop().first)
                    append(");")
                }
                stack.push(output to typeId)
            }

            Opcodes.L2I, Opcodes.F2I, Opcodes.D2I -> cast("I")
            Opcodes.I2L, Opcodes.F2L, Opcodes.D2L -> cast("J")
            Opcodes.I2F, Opcodes.L2F, Opcodes.D2F -> cast("F")
            Opcodes.I2D, Opcodes.L2D, Opcodes.F2D -> cast("D")
            Opcodes.I2B -> cast("B")
            Opcodes.I2C -> cast("C")
            Opcodes.I2S -> cast("S")

            Opcodes.IADD -> binaryOp("I", "BinaryOp.ADD")
            Opcodes.LADD -> binaryOp("J", "BinaryOp.ADD")
            Opcodes.FADD -> binaryOp("F", "BinaryOp.ADD")
            Opcodes.DADD -> binaryOp("D", "BinaryOp.ADD")

            Opcodes.ISUB -> binaryOp("I", "BinaryOp.SUBTRACT")
            Opcodes.LSUB -> binaryOp("J", "BinaryOp.SUBTRACT")
            Opcodes.FSUB -> binaryOp("F", "BinaryOp.SUBTRACT")
            Opcodes.DSUB -> binaryOp("D", "BinaryOp.SUBTRACT")

            Opcodes.IMUL -> binaryOp("I", "BinaryOp.MULTIPLY")
            Opcodes.LMUL -> binaryOp("J", "BinaryOp.MULTIPLY")
            Opcodes.FMUL -> binaryOp("F", "BinaryOp.MULTIPLY")
            Opcodes.DMUL -> binaryOp("D", "BinaryOp.MULTIPLY")

            Opcodes.IDIV -> binaryOp("I", "BinaryOp.DIVIDE")
            Opcodes.LDIV -> binaryOp("J", "BinaryOp.DIVIDE")
            Opcodes.FDIV -> binaryOp("F", "BinaryOp.DIVIDE")
            Opcodes.DDIV -> binaryOp("D", "BinaryOp.DIVIDE")

            Opcodes.IREM -> binaryOp("I", "BinaryOp.REMAINDER")
            Opcodes.LREM -> binaryOp("J", "BinaryOp.REMAINDER")
            Opcodes.FREM -> binaryOp("F", "BinaryOp.REMAINDER")
            Opcodes.DREM -> binaryOp("D", "BinaryOp.REMAINDER")

            Opcodes.INEG -> binaryOp("I", "BinaryOp.NEGATE")
            Opcodes.LNEG -> binaryOp("J", "BinaryOp.NEGATE")
            Opcodes.FNEG -> binaryOp("F", "BinaryOp.NEGATE")
            Opcodes.DNEG -> binaryOp("D", "BinaryOp.NEGATE")

            Opcodes.ISHL -> binaryOp("I", "BinaryOp.SHIFT_LEFT")
            Opcodes.LSHL -> binaryOp("J", "BinaryOp.SHIFT_LEFT")

            Opcodes.ISHR -> binaryOp("I", "BinaryOp.SHIFT_RIGHT")
            Opcodes.LSHR -> binaryOp("J", "BinaryOp.SHIFT_RIGHT")

            Opcodes.IUSHR -> binaryOp("I", "BinaryOp.UNSIGNED_SHIFT_RIGHT")
            Opcodes.LUSHR -> binaryOp("J", "BinaryOp.UNSIGNED_SHIFT_RIGHT")

            Opcodes.IAND -> binaryOp("I", "BinaryOp.AND")
            Opcodes.LAND -> binaryOp("J", "BinaryOp.AND")

            Opcodes.IOR -> binaryOp("I", "BinaryOp.OR")
            Opcodes.LOR -> binaryOp("J", "BinaryOp.OR")

            Opcodes.IXOR -> binaryOp("I", "BinaryOp.XOR")
            Opcodes.LXOR -> binaryOp("J", "BinaryOp.XOR")

            Opcodes.LCMP -> {
                val typeId = "TypeId.INT"
                val output = newLocalName(typeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(TypeId.INT);")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.compareLongs(")
                    append(output)
                    append(", ")
                    append(stack[1].first)
                    append(", ")
                    append(stack[0].first)
                    append(");")
                    stack.pop()
                    stack.pop()
                }
                stack.push(output to typeId)
            }

            Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> {
                val typeId = "TypeId.INT"
                val output = newLocalName(typeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(TypeId.INT);")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.compareFloatingPoint(")
                    append(output)
                    append(", ")
                    append(stack[1].first)
                    append(", ")
                    append(stack[0].first)
                    append(", ")
                    append(if (opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPG) 1 else -1)
                    append(");")
                    stack.pop()
                    stack.pop()
                }
                stack.push(output to typeId)
            }

            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.returnValue(")
                    append(stack.pop().first)
                    append(");")
                    stack.clear()
                }
            }

            Opcodes.RETURN -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.returnVoid();")
                    stack.clear()
                }
            }

            Opcodes.DUP -> {
                if (!pendingNewInstance) {
                    stack.push(stack.peek())
                }
            }
            Opcodes.DUP_X1 -> {
                println("DUP_X1")
            }
            Opcodes.DUP_X2 -> {
                println("DUP_X2")
            }
            Opcodes.DUP2 -> {
                println("DUP2")
            }
            Opcodes.DUP2_X1 -> {
                println("DUP2_X1")
            }
            Opcodes.DUP2_X2 -> {
                println("DUP2_X2")
            }
            Opcodes.MONITORENTER -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.monitorEnter(")
                    append(stack.pop().first)
                    append(");")
                }
            }
            Opcodes.MONITOREXIT -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.monitorExit(")
                    append(stack.pop().first)
                    append(");")
                }
            }
            Opcodes.ATHROW -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.throwValue(")
                    append(stack.pop().first)
                    append(");")
                }
            }
        }

    }

    private fun binaryOp(type: String, op: String) {
        val typeId = type.typeId
        val output = newLocalName(typeId)
        appendDeclarationCodeBlock {
            append("Local ")
            append(output)
            append(" = methodCodeBlock.newLocal(")
            append(typeId)
            append(");")
        }
        appendOperationCodeBlock {
            append("methodCodeBlock.op(")
            append(op)
            append(", ")
            append(output)
            append(", ")
            append(stack[1].first)
            append(", ")
            append(stack[0].first)
            append(");")
            stack.pop()
            stack.pop()
        }
        stack.push(output to typeId)
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        when (opcode) {
            Opcodes.BIPUSH -> loadConstant("B", operand)
            Opcodes.SIPUSH -> loadConstant("S", operand)
            Opcodes.NEWARRAY -> visitTypeInsn(
                Opcodes.ANEWARRAY, when (operand) {
                    4 -> "Z"
                    5 -> "C"
                    6 -> "F"
                    7 -> "D"
                    8 -> "B"
                    9 -> "S"
                    10 -> "I"
                    11 -> "J"
                    else -> return
                }
            )

        }
    }

    override fun visitVarInsn(opcode: Int, index: Int) {
        when (opcode) {
            Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                val originLocalCount = currentMethodParameters.size + if (isStaticMethod) 0 else 1
                if (index < originLocalCount) {
                    if (index == 0 && !isStaticMethod) {
                        stack.push("methodCodeBlock.getThis(classId)" to "classId")
                    } else {
                        val realIndex = if (isStaticMethod) index else index - 1
                        val typeId = currentMethodParameters[realIndex].typeId
                        stack.push("methodCodeBlock.getParameter($realIndex, $typeId)" to typeId)
                    }
                } else {
                    stack.push(localNames["local$index"])
                }
            }
            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                store(stack.peek().second, localNames["local$index"]?.first)
            }
        }

    }

    private fun store(typeId: String, target: String? = null) {
        val output = target ?: run {
            newLocalName(typeId, true).also {
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(it)
                    append(" = methodCodeBlock.newLocal(")
                    append(typeId)
                    append(");")
                }
            }
        }
        appendOperationCodeBlock {
            append("methodCodeBlock.move(")
            append(output)
            append(", ")
            append(stack.pop().first)
            append(");")
        }
    }

    private var pendingNewInstance = false

    override fun visitTypeInsn(opcode: Int, type: String) {
        when (opcode) {
            Opcodes.NEW -> {
                pendingNewInstance = true
            }
            Opcodes.ANEWARRAY -> {
                val ownerTypeId = (if (type.length == 1) "[$type" else "[L$type").typeId
                val output = newLocalName(ownerTypeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(")
                    append(ownerTypeId)
                    append(");")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.newArray(")
                    append(output)
                    append(", ")
                    append(stack.pop().first)
                    append(");")
                }
                stack.push(output to ownerTypeId)
            }
            Opcodes.CHECKCAST -> {
                cast(type)
            }
            Opcodes.INSTANCEOF -> {
                val typeId = type.typeId
                val output = newLocalName(typeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(")
                    append(typeId)
                    append(");")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.instanceOfType(")
                    append(output)
                    append(", ")
                    append(stack.pop().first)
                    append(", ")
                    append(typeId)
                    append(");")
                }
                stack.push(output to typeId)
            }
        }
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        when (opcode) {
            Opcodes.GETSTATIC -> {
                val typeId = descriptor.typeId
                val output = newLocalName(typeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(")
                    append(typeId)
                    append(");")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.sget(")
                    append(owner.getFieldId(descriptor, name))
                    append(", ")
                    append(output)
                    append(");")
                }
                stack.push(output to typeId)
            }
            Opcodes.PUTSTATIC -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.sput(")
                    append(owner.getFieldId(descriptor, name))
                    append(", ")
                    append(stack.peek().first)
                    append(");")
                    if (popAfterPut) {
                        popAfterPut = false
                        stack.pop()
                    }
                }
            }
            Opcodes.GETFIELD -> {
                val typeId = descriptor.typeId
                val output = newLocalName(typeId)
                appendDeclarationCodeBlock {
                    append("Local ")
                    append(output)
                    append(" = methodCodeBlock.newLocal(")
                    append(typeId)
                    append(");")
                }
                appendOperationCodeBlock {
                    append("methodCodeBlock.iget(")
                    append(owner.getFieldId(descriptor, name))
                    append(", ")
                    append(output)
                    append(", ")
                    append(stack.pop().first)
                    append(");")
                }
                stack.push(output to typeId)
            }
            Opcodes.PUTFIELD -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.iput(")
                    append(owner.getFieldId(descriptor, name))
                    append(", ")
                    append(stack[1].first)
                    append(", ")
                    append(stack[0].first)
                    append(");")
                    stack.pop()
                    if (popAfterPut) {
                        popAfterPut = false
                        stack.pop()
                    }
                }
            }
        }
    }

    override fun visitInvokeDynamicInsn(
        name: String?, descriptor: String?, bootstrapMethodHandle: Handle?, vararg bootstrapMethodArguments: Any?
    ) {
        //FIXME: 不支持INVOKE DYNAMIC
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        when (opcode) {
            Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.compareZ(")
                    append(
                        when (opcode) {
                            Opcodes.IFEQ -> "Comparison.EQ"
                            Opcodes.IFNE -> "Comparison.NE"
                            Opcodes.IFLT -> "Comparison.LT"
                            Opcodes.IFGE -> "Comparison.GE"
                            Opcodes.IFGT -> "Comparison.GT"
                            Opcodes.IFLE -> "Comparison.LE"
                            else -> ""
                        }
                    )
                    append(", ")
                    append(label)
                    append(", ")
                    append(stack.pop().first)
                    append(");")
                }
            }
            Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
            Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.compare(")
                    append(
                        when (opcode) {
                            Opcodes.IF_ICMPEQ, Opcodes.IF_ACMPEQ -> "Comparison.EQ"
                            Opcodes.IF_ICMPNE, Opcodes.IF_ACMPNE -> "Comparison.NE"
                            Opcodes.IF_ICMPLT -> "Comparison.LT"
                            Opcodes.IF_ICMPGE -> "Comparison.GE"
                            Opcodes.IF_ICMPGT -> "Comparison.GT"
                            Opcodes.IF_ICMPLE -> "Comparison.LE"
                            else -> ""
                        }
                    )
                    append(", ")
                    append(label)
                    append(", ")
                    append(stack[1].first)
                    append(", ")
                    append(stack[0].first)
                    append(");")
                    stack.pop()
                    stack.pop()
                }
            }
            Opcodes.IFNULL -> {
                visitInsn(Opcodes.ACONST_NULL)
                visitJumpInsn(Opcodes.IF_ACMPEQ, label)
            }
            Opcodes.IFNONNULL -> {
                visitInsn(Opcodes.ACONST_NULL)
                visitJumpInsn(Opcodes.IF_ACMPNE, label)
            }
            Opcodes.GOTO -> {
                appendOperationCodeBlock {
                    append("methodCodeBlock.jump(")
                    append(label)
                    append(");")
                }
            }
        }
    }

    private val labelMapping = HashSet<Label>()

    override fun visitLabel(label: Label) {
        if (!labelMapping.contains(label)) {
            labelMapping.add(label)
            appendDeclarationCodeBlock {
                append("Label ")
                append(label)
                append(" = new Label();")
            }
            appendOperationCodeBlock {
                append("methodCodeBlock.mark(")
                append(label)
                append(");")
            }
        }
    }

    private var popAfterPut = false

    override fun visitLdcInsn(value: Any) {
        when (value) {
            is Float -> loadConstant("F", "${value}F")
            is Double -> loadConstant("D", "${value}D")
            is Int -> loadConstant("I", value)
            is Long -> loadConstant("J", "${value}L")
            is String -> loadConstant("java/lang/String", "\"$value\"")
        }
        popAfterPut = true
    }

    override fun visitIincInsn(value: Int, increment: Int) {
        val output = localNames["local$value"]?.first ?: return
        loadConstant("I", increment)
        appendOperationCodeBlock {
            append("methodCodeBlock.op(")
            append("BinaryOp.ADD")
            append(", ")
            append(output)
            append(", ")
            append(output)
            append(", ")
            append(stack.pop().first)
            append(");")
        }
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {

    }

    override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray, labels: Array<out Label>) {
        val target = stack.peek()
        repeat(keys.size) { index ->
            loadConstant("I", keys[index])
            visitJumpInsn(Opcodes.IF_ICMPEQ, labels[index])
            stack.push(target)
        }
        stack.pop()
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {

    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String) {
        appendOperationCodeBlock {
            append("methodCodeBlock.addCatchClause(")
            append(type.typeId)
            append(", ")
            append(handler)
            append(");")
        }
    }

    override fun visitLocalVariable(
        name: String?, descriptor: String?, signature: String?, start: Label?, end: Label?, index: Int
    ) {

    }

    override fun visitLineNumber(line: Int, start: Label?) {

    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {

    }

    override fun visitMethodEnd() {
        flushTempCodeBlock()
        appendCodeBlockAndAdd {
            append("}")
        }
        labelMapping.clear()
        localCount = 0
        tempLocalCount = 0
        localNames.clear()
    }

    private fun appendCodeBlockAndAdd(block: StringBuilder.() -> Unit) {
        text.add(stringBuilder.apply {
            setLength(0)
            block()
            newLine()
        }.toString())
    }

    private fun appendCodeBlock(block: StringBuilder.() -> Unit) = stringBuilder.apply {
        setLength(0)
        block()
    }.toString()

    override fun visitModule(name: String?, access: Int, version: String?): Printer {
        return this
    }

    override fun visitNestHost(nestHost: String?) {

    }

    override fun visitClassTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): Printer {
        return this
    }

    override fun visitNestMember(nestMember: String?) {

    }

    override fun visitPermittedSubclass(permittedSubclass: String?) {

    }

    override fun visitRecordComponent(name: String?, descriptor: String?, signature: String?): Printer {
        return this
    }

    override fun visitMainClass(mainClass: String?) {
    }

    override fun visitPackage(packaze: String?) {
    }

    override fun visitRequire(module: String?, access: Int, version: String?) {
    }

    override fun visitExport(packaze: String?, access: Int, vararg modules: String?) {
    }

    override fun visitOpen(packaze: String?, access: Int, vararg modules: String?) {
    }

    override fun visitUse(service: String?) {
    }

    override fun visitProvide(service: String?, vararg providers: String?) {
    }

    override fun visitModuleEnd() {
    }

    override fun visitRecordComponentAnnotation(descriptor: String?, visible: Boolean): Printer {
        return this
    }

    override fun visitRecordComponentTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): Printer {
        return this
    }

    override fun visitRecordComponentAttribute(attribute: Attribute?) {
    }

    override fun visitRecordComponentEnd() {
    }

    override fun visitFieldTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): Printer {
        return this
    }

    override fun visitParameter(name: String?, access: Int) {
    }

    override fun visitMethodTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): Printer {
        return this
    }

    override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean): Printer {
        return this
    }

    override fun visitMethodInsn(
        opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
    ) {
        val returnType = descriptor.getReturnType()
        val returnTypeId = returnType.typeId
        val parameterTypes = descriptor.getParameterTypes()
        val isVoid = returnType == "V"
        val methodId = owner.getMethodId(name, returnType, parameterTypes)
        if (pendingNewInstance) {
            val ownerTypeId = owner.typeId
            val output = newLocalName(ownerTypeId)
            appendDeclarationCodeBlock {
                append("Local ")
                append(output)
                append(" = methodCodeBlock.newLocal(")
                append(ownerTypeId)
                append(");")
            }
            appendOperationCodeBlock {
                append("methodCodeBlock.newInstance(")
                append(output)
                append(", ")
                append(methodId)
                append(", ")
                val parameterCount = parameterTypes.size
                repeat(parameterCount) { index ->
                    append(stack[parameterCount - index - 1].first)
                    append(", ")
                }
                repeat(parameterCount) {
                    stack.pop()
                }
                deleteCharAt(lastIndex)
                deleteCharAt(lastIndex)
                append(");")
            }
            stack.push(output to ownerTypeId)
            pendingNewInstance = false
            return
        }
        var output = ""
        if (!isVoid) {
            output = newLocalName(returnTypeId)
            appendDeclarationCodeBlock {
                append("Local ")
                append(output)
                append(" = methodCodeBlock.newLocal(")
                append(returnTypeId)
                append(");")
            }
        }
        appendOperationCodeBlock {
            append("methodCodeBlock.")
            when (opcode) {
                Opcodes.INVOKEVIRTUAL -> append("invokeVirtual(")
                Opcodes.INVOKESPECIAL -> append("invokeDirect(")
                Opcodes.INVOKESTATIC -> append("invokeStatic(")
                Opcodes.INVOKEINTERFACE -> append("invokeInterface(")
            }
            append(methodId)
            append(", ")
            append(if (isVoid) "null" else output)
            append(", ")
            val parameterCount = parameterTypes.size + (if (opcode == Opcodes.INVOKESTATIC) 0 else 1)
            repeat(parameterCount) { index ->
                append(stack[parameterCount - index - 1].first)
                append(", ")
            }
            repeat(parameterCount) {
                stack.pop()
            }
            deleteCharAt(lastIndex)
            deleteCharAt(lastIndex)
            append(");")
        }
        if (!isVoid) {
            stack.push(output to returnTypeId)
        }
    }

    override fun visitInsnAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): Printer {
        return this
    }

    override fun visitTryCatchAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): Printer {
        return this
    }

    override fun visitLocalVariableAnnotation(
        typeRef: Int, typePath: TypePath?, start: Array<out Label>?,
        end: Array<out Label>?, index: IntArray?, descriptor: String?, visible: Boolean
    ): Printer {
        return this
    }

    companion object {
        private const val ACCESS_CLASS = 0x40000
        private const val ACCESS_FIELD = 0x80000
    }
}