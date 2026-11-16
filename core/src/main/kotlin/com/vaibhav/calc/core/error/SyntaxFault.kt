package com.vaibhav.calc.core.error

import com.vaibhav.calc.core.source.SourceSpan

/**
 * Internal syntax fault thrown by the tokenizer / parser and caught at the
 * public parse boundary, where it becomes an [EvalError.Syntax]. Carries the
 * source span so the boundary can report *where* — Article 3 / spec 1.8.
 */
class SyntaxFault(message: String, val span: SourceSpan) : RuntimeException(message)
