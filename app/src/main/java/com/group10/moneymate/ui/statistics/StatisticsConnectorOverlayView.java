package com.group10.moneymate.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StatisticsConnectorOverlayView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<ConnectorSpec> connectors = new ArrayList<>();
    private float animationProgress = 1f;

    public StatisticsConnectorOverlayView(Context context) {
        super(context);
        init();
    }

    public StatisticsConnectorOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StatisticsConnectorOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeWidth(dp(1.5f));
    }

    public void setConnectors(@Nullable List<ConnectorSpec> connectorSpecs) {
        connectors.clear();
        if (connectorSpecs != null) {
            connectors.addAll(connectorSpecs);
        }
        animationProgress = 1f;
        invalidate();
    }

    public void clearConnectors() {
        connectors.clear();
        animationProgress = 0f;
        invalidate();
    }

    public void setAnimationProgress(float animationProgress) {
        this.animationProgress = Math.max(0f, Math.min(1f, animationProgress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (ConnectorSpec connector : connectors) {
            linePaint.setColor(connector.color);
            drawRoundedConnector(canvas, connector);
        }
    }

    private void drawRoundedConnector(@NonNull Canvas canvas, @NonNull ConnectorSpec connector) {
        float cornerRadius = dp(6f);
        float animatedJointX = connector.startX + ((connector.jointX - connector.startX) * animationProgress);
        float animatedJointY = connector.startY + ((connector.jointY - connector.startY) * animationProgress);
        float animatedEndX = connector.jointX + ((connector.endX - connector.jointX) * animationProgress);
        float animatedEndY = connector.jointY + ((connector.endY - connector.jointY) * animationProgress);
        float direction = animatedEndX >= animatedJointX ? 1f : -1f;
        float preCornerX = animatedJointX - (cornerRadius * direction);
        float postCornerY = animatedJointY;

        Path path = new Path();
        path.moveTo(connector.startX, connector.startY);
        path.lineTo(animatedJointX, animatedJointY);
        path.lineTo(preCornerX, animatedJointY);
        path.quadTo(animatedJointX, animatedJointY, animatedJointX, postCornerY);
        path.lineTo(animatedEndX, animatedEndY);
        canvas.drawPath(path, linePaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    public static final class ConnectorSpec {
        public final float startX;
        public final float startY;
        public final float jointX;
        public final float jointY;
        public final float endX;
        public final float endY;
        @ColorInt
        public final int color;

        public ConnectorSpec(float startX,
                             float startY,
                             float jointX,
                             float jointY,
                             float endX,
                             float endY,
                             @ColorInt int color) {
            this.startX = startX;
            this.startY = startY;
            this.jointX = jointX;
            this.jointY = jointY;
            this.endX = endX;
            this.endY = endY;
            this.color = color;
        }
    }
}
