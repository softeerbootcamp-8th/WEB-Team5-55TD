package com.ootd.pickup.global.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/** {@link DistributedLock#key()}에 선언된 SpEL 표현식을 실제 메서드 인자 값으로 바인딩해 락 키 문자열로 변환한다. */
@Component
public class LockKeyParser {

  private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
      new DefaultParameterNameDiscoverer();
  private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

  public String parse(ProceedingJoinPoint joinPoint, String keyExpression) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(signature.getMethod());
    Object[] args = joinPoint.getArgs();

    StandardEvaluationContext context = new StandardEvaluationContext();
    if (parameterNames != null) {
      for (int i = 0; i < parameterNames.length; i++) {
        context.setVariable(parameterNames[i], args[i]);
      }
    }

    Expression expression = EXPRESSION_PARSER.parseExpression(keyExpression);
    return expression.getValue(context, String.class);
  }
}
