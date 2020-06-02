package itmo.nolan.kotlingen

import org.antlr.runtime.CommonToken
import org.antlr.v4.Tool
import org.antlr.v4.parse.ANTLRParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.tool.ANTLRMessage
import org.antlr.v4.tool.ANTLRToolListener
import org.antlr.v4.tool.Grammar
import org.antlr.v4.tool.LexerGrammar
import org.antlr.v4.tool.ast.*
import java.lang.UnsupportedOperationException
import kotlin.random.Random

fun readResource(resourceName: String): String {
    return Object::class.java.getResource(resourceName).readText()
}

fun parseFile(lexer: LexerGrammar, parser: Grammar, input: CharStream): ParserRuleContext? {
    val lexerInterpreter = lexer.createLexerInterpreter(input)
    val tokenStream: TokenStream = CommonTokenStream(lexerInterpreter, 0)
    val parserInterpreter = parser.createParserInterpreter(tokenStream)
    return parserInterpreter.parse(parserInterpreter.getRuleIndex("kotlinFile"))
}

fun repeatList(l: List<Any>, atLeast: Int, atMost: Int): List<GrammarAST> {
    val n = atLeast + Random.nextInt(atMost - atLeast + 1)
    return generateSequence { 0 }.flatMap { l.asSequence() }.take(n).toList() as List<GrammarAST>
}

fun choose(l: List<Any>): GrammarAST = l[Random.nextInt(l.size)] as GrammarAST

var iteration = 0

fun countNonTerminals(ast: List<GrammarAST>): Long = ast.stream().filter{ it !is TerminalAST }.count()

fun expand(ast: GrammarAST): List<GrammarAST> {
    val stopIteration = 5
    val nMax = 2
    when (ast) {
//        is SetAST -> {
//            throw UnsupportedOperationException("SetAST node is unsupported")
//        }
        is BlockAST -> {
            return ast.children as List<GrammarAST>
        }
        is ActionAST -> {
            return listOf()
        }
        is RuleRefAST -> {
            return listOf(ast.g.rules[ast.token.text]!!.alt[1].ast)
        }
        is AltAST -> {
            return ast.children as List<GrammarAST>
        }
        is TerminalAST -> {
            return listOf(ast)
        }
        is OptionalBlockAST -> {
            return repeatList(ast.children, 0, if (iteration > stopIteration) 0 else 1)
        }
        is PlusBlockAST -> {
            return repeatList(ast.children, 1, if (iteration > stopIteration) 1 else nMax)
        }
        is StarBlockAST -> {
            return repeatList(ast.children, 0, if (iteration > stopIteration) 0 else nMax)
        }
        is GrammarAST -> {
            if (ast.token.type == ANTLRParser.SET) {
                if (iteration > stopIteration) {
                    val tokens = ast.children as List<GrammarAST>
                    return listOf(tokens.stream().min(Comparator.comparing<GrammarAST, Long> { countNonTerminals(it.children as List<GrammarAST>) }).get())
                } else {
                    return listOf(choose(ast.children))
                }
            }
            throw UnsupportedOperationException("GrammarAST node is unsupported")
        }
        else -> {
            throw UnsupportedOperationException(ast.javaClass.name + " node type is unsupported")
        }
    }
}

fun expand(tokens: List<GrammarAST>) = tokens.flatMap { expand(it) }

fun main() {
//    val listener = object : ANTLRToolListener {
//        override fun warning(msg: ANTLRMessage?) {
//            println("WARNING: ${msg.toString()}")
//        }
//
//        override fun info(msg: String?) {
//            println("   INFO: $msg")
//        }
//
//        override fun error(msg: ANTLRMessage?) {
//            println("  ERROR: ${msg.toString()}")
//        }
//    }
    val tool = Tool()
//    tool.addListener(listener)
    val parser = tool.loadGrammar("grammar/KotlinParser1.g4")
    val kotlinFileRuleRef = RuleRefAST(CommonToken(57, "expression"))
    kotlinFileRuleRef.g = parser

    var prevTokens: List<GrammarAST> = listOf()
    var tokens: List<GrammarAST> = listOf(kotlinFileRuleRef)
    for (i in 0..100) {
//        println("$i: ${tokensToString(tokens).length}")
        println("$i: ${tokensToString(tokens)}")
        if (prevTokens == tokens) break
        prevTokens = tokens
        iteration = i
        tokens = expand(tokens)
    }
//    println("${tokensToString(tokens)}")
}

fun tokensToString(tokens: List<GrammarAST>): String {
    return tokens.asSequence().map { tokenToString(it) }.joinToString(" ")
}

fun alternativesToString(tokens: List<GrammarAST>): String {
    val t = tokens.asSequence().map { tokenToString(it) }.joinToString(" | ")
    return if (tokens.size > 1) {
        "($t)"
    } else {
        t
    }
}

fun tokenToString(ast: GrammarAST): String {
    when (ast) {
        is SetAST -> {
            return alternativesToString(ast.children as List<GrammarAST>)
        }
        is BlockAST -> {
            return tokensToString(ast.children as List<GrammarAST>)
        }
        is RuleRefAST -> {
            return "<rule:${ast.token.text}>"
        }
        is AltAST -> {
            return tokensToString(ast.children as List<GrammarAST>)
        }
        is TerminalAST -> {
            val token = ast.token.text
            return if (token.first() == '\'' && token.last() == '\'') {
                token.substring(1, token.length - 1)
            } else {
                "<$token>"
            }
        }
        is OptionalBlockAST -> {
            return "${tokensToString(ast.children as List<GrammarAST>)}?"
        }
        is PlusBlockAST -> {
            return "${tokensToString(ast.children as List<GrammarAST>)}+"
        }
        is StarBlockAST -> {
            return "${tokensToString(ast.children as List<GrammarAST>)}*"
        }
        is GrammarAST -> {
            if (ast.token.type == ANTLRParser.SET) {
                return alternativesToString(ast.children as List<GrammarAST>)
            }
            return "<${ast.javaClass.name}>"
        }
        else -> {
            return "<???${ast.javaClass.name}???>"
        }
    }
}

