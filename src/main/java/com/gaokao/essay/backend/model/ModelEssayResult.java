package com.gaokao.essay.backend.model;

import java.util.ArrayList;
import java.util.List;

public class ModelEssayResult {

  public String targetBand = "高分提升版";
  public String modelEssay = "";
  public List<ParagraphInsight> paragraphInsights = new ArrayList<>();
  public List<ExpressionComparison> expressionComparisons = new ArrayList<>();
  public List<String> reusableExpressions = new ArrayList<>();
  public long generatedAt = 0L;

  public static class ParagraphInsight {
    public String title = "";
    public String purpose = "";
    public String keyExpression = "";
  }

  public static class ExpressionComparison {
    public String original = "";
    public String recommended = "";
    public String reason = "";
  }
}
