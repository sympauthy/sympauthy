package com.sympauthy.expression

import com.ezylang.evalex.EvaluationException
import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import io.micronaut.core.convert.ConversionService
import java.time.Duration

/**
 * Custom function checking whether the person's authentication behind the token is no older than a duration.
 * Return true or false, and false where no person authenticated behind the token.
 *
 * The duration is read the way every duration key of the configuration is, through the same conversion, so
 * a deployment writes `5m` here as it does anywhere else. One that does not read is refused rather than
 * answered, and it is read before anything else so that the configuration, which evaluates the function with
 * no authentication behind it, refuses it at startup.
 */
@FunctionParameter(name = "duration")
class AuthenticationIsYoungerThanFunction(
    /**
     * How long ago the person authenticated, or null where no person is behind the token.
     */
    private val authenticationAge: Duration? = null
) : AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val literal = parameterValues[0].stringValue
        val duration = readDurationOrNull(literal)
            ?: throw EvaluationException(functionToken, "$literal is not a duration")
        val authenticationAge = this.authenticationAge ?: return EvaluationValue.booleanValue(false)
        return EvaluationValue.booleanValue(authenticationAge <= duration)
    }

    companion object {
        const val FUNCTION_NAME = "AUTHENTICATION_IS_YOUNGER_THAN"

        /**
         * Read [literal] the way every duration key of the configuration is, or return null where it is not a
         * duration.
         */
        fun readDurationOrNull(literal: String?): Duration? =
            literal?.let { ConversionService.SHARED.convert(it, Duration::class.java).orElse(null) }
    }
}
