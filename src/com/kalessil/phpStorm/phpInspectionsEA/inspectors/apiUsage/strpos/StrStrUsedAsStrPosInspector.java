package com.kalessil.phpStorm.phpInspectionsEA.inspectors.apiUsage.strpos;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.elements.BinaryExpression;
import com.jetbrains.php.lang.psi.elements.ConstantReference;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.UnaryExpression;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class StrStrUsedAsStrPosInspector extends BasePhpInspection {
    private static final String strProblemDescription  = "'false %o% %f%(%s%, %p%)' should be used instead";

    @NotNull
    public String getShortName() {
        return "StrStrUsedAsStrPosInspection";
    }

    private static HashMap<String, String> mapping = null;
    private static HashMap<String, String> getMapping() {
        if (null == mapping) {
            mapping = new HashMap<String, String>();

            mapping.put("strstr", "strpos");
            mapping.put("stristr", "stripos");
        }

        return mapping;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpFunctionCall(FunctionReference reference) {
                HashMap<String, String> mapping = getMapping();

                /* check if it's the target function */
                final String strFunctionName = reference.getName();
                final PsiElement[] params = reference.getParameters();
                if (params.length < 2 || StringUtil.isEmpty(strFunctionName) || !mapping.containsKey(strFunctionName)) {
                    return;
                }

                /* checks implicit boolean comparison pattern */
                if (reference.getParent() instanceof BinaryExpression) {
                    final BinaryExpression parent = (BinaryExpression) reference.getParent();
                    final PsiElement operation    = parent.getOperation();
                    if (null != operation && null != operation.getNode()) {
                        IElementType operationType = operation.getNode().getElementType();
                        if (
                            operationType == PhpTokenTypes.opIDENTICAL || operationType == PhpTokenTypes.opNOT_IDENTICAL ||
                            operationType == PhpTokenTypes.opEQUAL     || operationType == PhpTokenTypes.opNOT_EQUAL
                        ) {
                            /* get second operand */
                            PsiElement secondOperand = parent.getLeftOperand();
                            if (secondOperand == reference) {
                                secondOperand = parent.getRightOperand();
                            }

                            /* verify if operand is a boolean and report an issue */
                            if (secondOperand instanceof ConstantReference && ExpressionSemanticUtil.isBoolean((ConstantReference) secondOperand)) {
                                final String operator = operation.getText();
                                final String message = strProblemDescription
                                        .replace("%o%", operator.length() == 2 ? operator + "=": operator)
                                        .replace("%f%", mapping.get(strFunctionName))
                                        .replace("%s%", params[0].getText())
                                        .replace("%p%", params[1].getText())
                                ;
                                holder.registerProblem(parent, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

                                return;
                            }
                        }
                    }
                }

                /* checks NON-implicit boolean comparison patternS */
                if (ExpressionSemanticUtil.isUsedAsLogicalOperand(reference)) {
                    final String message = strProblemDescription
                            .replace("%o%", reference.getParent() instanceof UnaryExpression ? "===": "!==")
                            .replace("%f%", mapping.get(strFunctionName))
                            .replace("%s%", params[0].getText())
                            .replace("%p%", params[1].getText())
                    ;
                    holder.registerProblem(reference, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

                    //return;
                }
            }
        };
    }
}
