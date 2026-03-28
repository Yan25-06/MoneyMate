package com.group10.moneymate.ui.budget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.group10.moneymate.R;

public class BudgetProjectionChartView extends View {

    private final Paint guidelinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint budgetLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actualLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint projectedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actualFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint projectedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();

    private double budgetAmount;
    private double spentAmount;
    private double projectedAmount;
    private float progressFraction;
    private long startDate;
    private long endDate;

    public BudgetProjectionChartView(Context context) {
        this(context, null);
    }

    public BudgetProjectionChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BudgetProjectionChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        guidelinePaint.setColor(ContextCompat.getColor(getContext(), R.color.budget_divider));
        guidelinePaint.setStrokeWidth(dp(1f));
        guidelinePaint.setStyle(Paint.Style.STROKE);

        axisPaint.setColor(ContextCompat.getColor(getContext(), R.color.md_theme_primary));
        axisPaint.setStrokeWidth(dp(1.5f));
        axisPaint.setStyle(Paint.Style.STROKE);

        budgetLinePaint.setColor(ContextCompat.getColor(getContext(), R.color.budget_danger_red));
        budgetLinePaint.setStrokeWidth(dp(1.5f));
        budgetLinePaint.setStyle(Paint.Style.STROKE);

        int green = ContextCompat.getColor(getContext(), R.color.budget_safe_green);
        actualLinePaint.setColor(green);
        actualLinePaint.setStrokeWidth(dp(3f));
        actualLinePaint.setStyle(Paint.Style.STROKE);

        projectedLinePaint.setColor(green);
        projectedLinePaint.setStrokeWidth(dp(2.5f));
        projectedLinePaint.setStyle(Paint.Style.STROKE);
        projectedLinePaint.setPathEffect(new DashPathEffect(new float[]{dp(6f), dp(5f)}, 0f));

        actualFillPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(green, 68));
        actualFillPaint.setStyle(Paint.Style.FILL);

        projectedFillPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(green, 36));
        projectedFillPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.budget_text_secondary));
        labelPaint.setTextSize(sp(11f));
    }

    public void setBudgetData(double budgetAmount,
                              double spentAmount,
                              double projectedAmount,
                              float progressFraction,
                              long startDate,
                              long endDate) {
        this.budgetAmount = Math.max(budgetAmount, 0d);
        this.spentAmount = Math.max(spentAmount, 0d);
        this.projectedAmount = Math.max(projectedAmount, 0d);
        this.progressFraction = Math.max(0f, Math.min(1f, progressFraction));
        this.startDate = startDate;
        this.endDate = endDate;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float leftPadding = dp(56f);
        float rightPadding = dp(12f);
        float top = dp(14f);
        float bottomPadding = dp(28f);
        float left = leftPadding;
        float right = getWidth() - rightPadding;
        float bottom = getHeight() - bottomPadding;
        float width = right - left;
        float height = bottom - top;
        if (width <= 0f || height <= 0f) {
            return;
        }

        double maxValue = getNiceMaxValue(Math.max(budgetAmount, Math.max(projectedAmount, spentAmount)));
        float budgetY = valueToY(budgetAmount, maxValue, top, bottom);
        float currentX = left + (width * progressFraction);
        float currentY = valueToY(spentAmount, maxValue, top, bottom);
        float projectedY = valueToY(projectedAmount, maxValue, top, bottom);

        for (int i = 0; i <= 4; i++) {
            float y = top + ((height / 4f) * i);
            canvas.drawLine(left, y, right, y, guidelinePaint);

            double axisValue = maxValue - ((maxValue / 4d) * i);
            drawYLabel(canvas, BudgetUiUtils.formatAxisMoney(axisValue), left, y);
        }

        canvas.drawLine(left, budgetY, right, budgetY, budgetLinePaint);
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        Path actualFill = new Path();
        actualFill.moveTo(left, bottom);
        actualFill.lineTo(currentX, bottom);
        actualFill.lineTo(currentX, currentY);
        actualFill.close();
        canvas.drawPath(actualFill, actualFillPaint);

        Path actualLine = new Path();
        actualLine.moveTo(left, bottom);
        actualLine.lineTo(currentX, currentY);
        canvas.drawPath(actualLine, actualLinePaint);

        Path projectedFill = new Path();
        projectedFill.moveTo(currentX, bottom);
        projectedFill.lineTo(currentX, currentY);
        projectedFill.lineTo(right, projectedY);
        projectedFill.lineTo(right, bottom);
        projectedFill.close();
        canvas.drawPath(projectedFill, projectedFillPaint);

        Path projectedLine = new Path();
        projectedLine.moveTo(currentX, currentY);
        projectedLine.lineTo(right, projectedY);
        canvas.drawPath(projectedLine, projectedLinePaint);

        drawXLabels(canvas, left, right, bottom);
    }

    private float valueToY(double value, double maxValue, float top, float bottom) {
        float fraction = (float) Math.max(0d, Math.min(1d, value / maxValue));
        return bottom - ((bottom - top) * fraction);
    }

    private void drawYLabel(@NonNull Canvas canvas, @NonNull String text, float chartLeft, float y) {
        labelPaint.getTextBounds(text, 0, text.length(), textBounds);
        float baseline = y + (textBounds.height() / 2f);
        canvas.drawText(text, dp(4f), baseline, labelPaint);
    }

    private void drawXLabels(@NonNull Canvas canvas, float left, float right, float bottom) {
        if (startDate <= 0L || endDate <= 0L) {
            return;
        }
        String startText = BudgetUiUtils.formatAxisDate(startDate);
        String endText = BudgetUiUtils.formatAxisDate(endDate);
        float textY = bottom + dp(20f);
        canvas.drawText(startText, left, textY, labelPaint);
        labelPaint.getTextBounds(endText, 0, endText.length(), textBounds);
        canvas.drawText(endText, right - textBounds.width(), textY, labelPaint);
    }

    private double getNiceMaxValue(double rawMax) {
        double positiveMax = Math.max(rawMax, 1d);
        double exponent = Math.pow(10d, Math.floor(Math.log10(positiveMax)));
        double fraction = positiveMax / exponent;
        double niceFraction;
        if (fraction <= 1d) {
            niceFraction = 1d;
        } else if (fraction <= 2d) {
            niceFraction = 2d;
        } else if (fraction <= 5d) {
            niceFraction = 5d;
        } else {
            niceFraction = 10d;
        }
        return niceFraction * exponent;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
