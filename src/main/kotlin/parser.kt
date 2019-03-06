import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.io.*

data class Node(val body: MutableList<String>) {
    var numStacktrace: Int = 0
        private set
    private var childrenMap: MutableMap<String, Node> = mutableMapOf()

    val children: List<Node> get() = childrenMap.values.sortedByDescending { it.numStacktrace }

    val isLeaf get() = children.isEmpty()

    constructor(line: String) : this(mutableListOf(line))

    private fun addStacktrace(line: String): Node =
        childrenMap.computeIfAbsent(regex.find(line)?.groupValues?.get(1) ?: line) { Node(line) }

    fun addStacktrace(lines: List<String>) {
        if (!lines.isEmpty()) {
            addStacktrace(lines.first()).apply { numStacktrace++ }.addStacktrace(lines.drop(1))
        }
    }

    fun prune() {
        childrenMap.values.forEach { it.prune() }
        if (childrenMap.size == 1) {
            val singleChild = childrenMap.values.first()
            body.addAll(singleChild.body)
            childrenMap = singleChild.childrenMap
            numStacktrace = singleChild.numStacktrace
        }
    }
}

private val regex = Regex("\\s*at .*\\((.*)\\)$")

fun main(args: Array<String>) {
    val root = Node("root")
    BufferedReader(FileReader(args[0])).use {
        val linesOfStackTrace = ArrayList<String>()
        for (line in it.lineSequence()) {
            when {
                line.startsWith("*****") -> {
                    root.addStacktrace(linesOfStackTrace.asReversed())
                    linesOfStackTrace.clear()
                }
                line.startsWith("WARNING:") -> {
                    root.addStacktrace(linesOfStackTrace.asReversed())
                    return@use
                }
                else -> linesOfStackTrace.add(line.trim())
            }
        }
    }
    root.prune()
    PrintStream(BufferedOutputStream(FileOutputStream(args[0] + ".html"))).use {
        it.print(
            """
<!DOCTYPE html>
<meta charset="utf-8">
<!-- DO NOT SUBMIT: Fix Page Title. Missing tags? http://go/optional-html -->
<title>Page Title</title>
<script src="https://code.jquery.com/jquery-1.10.2.js"></script>
<script>
function clear(text) {
$("details > div:contains('"+text+"')").parent().remove();
$("details:not(:contains('LEAF'))").remove()
}
$('html').keyup(function(e){
    if(e.keyCode == 46) {
        const selection = window.getSelection().toString(); if (selection) clear(selection);
    }
});
</script>
<header>
<style>
body {
  font-family: monospace;
    transform: scale(1, -1);
}

details>* {
  margin-left: 19px;
}

div {
    transform: scale(1, -1);
    margin-left: 39px;
}

summary {
    transform: scale(1, -1);
}

summary::-webkit-details-marker {
  display: none
}
summary:after {
  content: "+";
  color: #000;
  float: left;
  font-weight: bold;
  padding: 0;
  text-align: center;
  width: 20px;
}
details[open] > summary:after {
  content: "-";
}

</style>
</header>
"""
        )
        it.appendHTML().body {
            root.toHtml(this)
        }
    }
}

fun Node.toHtml(htmlBody: BODY, level: Int = 0): Unit = htmlBody.run {
    details {
        summary {
            +"L$level ($numStacktrace)"
            if (isLeaf) {
                +" LEAF"
            }
        }
        body.forEach { div { +it } }
        children.forEach { it.toHtml(htmlBody, level + 1) }
    }
}

private class SUMMARY(consumer: TagConsumer<*>) :
    HTMLTag(
        "summary", consumer, emptyMap(),
        inlineTag = true,
        emptyTag = false
    ), HtmlInlineTag

private fun DETAILS.summary(block: SUMMARY.() -> Unit = {}) {
    SUMMARY(consumer).visit(block)
}
