package com.android.requi.parser

import java.util.regex.Pattern

class BcrTemplateParser(private val template: String, private val extension: String) {

    private sealed interface TemplateNode {
        data class Literal(val text: String) : TemplateNode
        data class Variable(val name: String, val format: String? = null) : TemplateNode
        data class Fallback(val choices: List<List<TemplateNode>>) : TemplateNode
    }

    private var regexPattern: Pattern? = null
    private val parsedNodes: List<TemplateNode>

    init {
        parsedNodes = parseTemplate(template)
        compileRegex()
    }

    /**
     * Parses the template string into a list of AST nodes (literals, variables, and fallbacks).
     */
    private fun parseTemplate(template: String): List<TemplateNode> {
        var index = 0

        fun parseNodes(stopChar: Char? = null, stopChar2: Char? = null): List<TemplateNode> {
            val nodes = mutableListOf<TemplateNode>()
            val literalBuilder = StringBuilder()

            fun flushLiteral() {
                if (literalBuilder.isNotEmpty()) {
                    nodes.add(TemplateNode.Literal(literalBuilder.toString()))
                    literalBuilder.setLength(0)
                }
            }

            while (index < template.length) {
                val char = template[index]
                if (stopChar != null && char == stopChar) {
                    break
                }
                if (stopChar2 != null && char == stopChar2) {
                    break
                }
                when (char) {
                    '\\' -> {
                        if (index + 1 < template.length) {
                            literalBuilder.append(template[index + 1])
                            index += 2
                        } else {
                            literalBuilder.append(char)
                            index++
                        }
                    }
                    '{' -> {
                        flushLiteral()
                        index++ // skip '{'
                        val start = index
                        var depth = 1
                        while (index < template.length && depth > 0) {
                            if (template[index] == '{') depth++
                            else if (template[index] == '}') depth--
                            index++
                        }
                        if (index <= template.length) {
                            val varText = template.substring(start, index - 1)
                            val parts = varText.split(':', limit = 2)
                            val varName = parts[0].trim()
                            val varFormat = parts.getOrNull(1)
                            nodes.add(TemplateNode.Variable(varName, varFormat))
                        }
                    }
                    '[' -> {
                        flushLiteral()
                        index++ // skip '['
                        val choices = mutableListOf<List<TemplateNode>>()
                        while (index < template.length) {
                            val choiceNodes = parseNodes('|', ']')
                            choices.add(choiceNodes)
                            if (index < template.length && template[index] == ']') {
                                index++ // skip ']'
                                break
                            }
                            if (index < template.length && template[index] == '|') {
                                index++ // skip '|'
                            }
                        }
                        nodes.add(TemplateNode.Fallback(choices))
                    }
                    else -> {
                        literalBuilder.append(char)
                        index++
                    }
                }
            }
            flushLiteral()
            return nodes
        }

        return parseNodes()
    }

    /**
     * Translates a list of TemplateNodes into a Regex pattern.
     */
    private fun compileRegex() {
        try {
            val sb = StringBuilder()
            sb.append("^")
            sb.append(nodesToRegex(parsedNodes))
            if (extension.lowercase() == "all" || extension.isBlank()) {
                sb.append("\\.[^.]+$")
            } else {
                val escapedExt = if (extension.startsWith(".")) extension else ".$extension"
                sb.append(Pattern.quote(escapedExt))
                sb.append("$")
            }

            regexPattern = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback pattern if compilation fails
            regexPattern = null
        }
    }

    private fun nodesToRegex(nodes: List<TemplateNode>): String {
        val sb = StringBuilder()
        for (node in nodes) {
            when (node) {
                is TemplateNode.Literal -> {
                    sb.append(Pattern.quote(node.text))
                }
                is TemplateNode.Variable -> {
                    val groupName = mapVarNameToGroupName(node.name)
                    val pattern = when (node.name) {
                        "direction" -> "in|out|conference"
                        "sim_slot" -> "\\d+"
                        "phone_number" -> "\\+?[\\d\\s\\-()]+"
                        "date" -> "[\\d_\\-.T:+\\s]+"
                        // Names can be anything except a dot (which prefixes the extension) or slash
                        else -> "[^./\\\\]+" 
                    }
                    sb.append("(?<$groupName>$pattern)")
                }
                is TemplateNode.Fallback -> {
                    val hasEmptyChoice = node.choices.any { it.isEmpty() }
                    val nonEmptyChoices = node.choices.filter { it.isNotEmpty() }
                    if (nonEmptyChoices.isEmpty()) {
                        // Ignore
                    } else if (nonEmptyChoices.size == 1) {
                        val choiceRegex = nodesToRegex(nonEmptyChoices[0])
                        if (hasEmptyChoice) {
                            sb.append("(?:$choiceRegex)?")
                        } else {
                            sb.append("(?:$choiceRegex)")
                        }
                    } else {
                        val choicesRegexList = nonEmptyChoices.map { nodesToRegex(it) }
                        val combined = choicesRegexList.joinToString("|")
                        if (hasEmptyChoice) {
                            sb.append("(?:(?:$combined))?")
                        } else {
                            sb.append("(?:$combined)")
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun mapVarNameToGroupName(name: String): String {
        return when (name) {
            "sim_slot" -> "simSlot"
            "phone_number" -> "phoneNumber"
            "contact_name" -> "contactName"
            "caller_name" -> "callerName"
            "call_log_name" -> "callLogName"
            "package_name" -> "packageName"
            else -> name // date, direction, etc.
        }
    }

    data class ParsedMetadata(
        val date: String? = null,
        val direction: String? = null,
        val simSlot: Int? = null,
        val phoneNumber: String? = null,
        val contactName: String? = null,
        val callerName: String? = null,
        val callLogName: String? = null
    )

    /**
     * Parses a filename using the compiled regex pattern and returns extracted metadata.
     */
    fun parseFilename(filename: String): ParsedMetadata {
        val pattern = regexPattern ?: return ParsedMetadata()
        val matcher = pattern.matcher(filename)
        if (!matcher.matches()) {
            return ParsedMetadata()
        }

        fun getGroupValue(groupName: String): String? {
            return try {
                matcher.group(groupName)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val date = getGroupValue("date")
        val direction = getGroupValue("direction")
        val simSlotStr = getGroupValue("simSlot")
        val simSlot = simSlotStr?.toIntOrNull()
        val phoneNumber = getGroupValue("phoneNumber")
        
        // Clean names of trailing/leading spaces or underscores if any
        val contactName = getGroupValue("contactName")?.replace('_', ' ')?.trim()
        val callerName = getGroupValue("callerName")?.replace('_', ' ')?.trim()
        val callLogName = getGroupValue("callLogName")?.replace('_', ' ')?.trim()

        return ParsedMetadata(
            date = date,
            direction = direction,
            simSlot = simSlot,
            phoneNumber = phoneNumber,
            contactName = contactName,
            callerName = callerName,
            callLogName = callLogName
        )
    }
}
