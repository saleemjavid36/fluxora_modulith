package com.springmodulith.plugin.toolwindow;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import org.jetbrains.annotations.NotNull;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModulithDependencyGraphPanel extends JPanel {

    private static final int NODE_WIDTH = 180;
    private static final int NODE_HEIGHT = 70;
    private static final int HORIZONTAL_GAP = 80;
    private static final int VERTICAL_GAP = 80;
    private static final int PADDING = 60;

    private final Map<String, Point> nodePositions = new HashMap<>();

    private ModulithDependencyGraph graph;
    private Consumer<ModulithModule> moduleSelectionListener;

    public ModulithDependencyGraphPanel() {
        setBackground(JBColor.background());
        setBorder(JBUI.Borders.empty(PADDING));
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(
                            MouseEvent event) {

                        handleModuleClick(
                                event.getPoint()
                        );
                    }
                }
        );
    }
    private void handleModuleClick(
            @NotNull Point point) {

        if (graph == null ||
                moduleSelectionListener == null) {

            return;
        }

        for (ModulithModule module :
                graph.getModules()) {

            Point position =
                    nodePositions.get(
                            module.getPackageName()
                    );

            if (position == null) {
                continue;
            }

            int x = position.x;
            int y = position.y;

            if (point.x >= x &&
                    point.x <= x + NODE_WIDTH &&
                    point.y >= y &&
                    point.y <= y + NODE_HEIGHT) {

                moduleSelectionListener.accept(
                        module
                );

                return;
            }
        }
    }
    public void setModuleSelectionListener(
            @NotNull Consumer<ModulithModule> listener) {

        this.moduleSelectionListener = listener;
    }

    public void setGraph(
            @NotNull ModulithDependencyGraph graph) {

        this.graph = graph;

        calculateLayout();

        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(
            @NotNull Graphics graphics) {

        super.paintComponent(graphics);

        if (graph == null) {
            return;
        }

        Graphics2D g =
                (Graphics2D) graphics.create();

        try {
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            paintDependencies(g);
            paintModules(g);
        } finally {
            g.dispose();
        }
    }

    private void paintModules(
            @NotNull Graphics2D g) {

        for (ModulithModule module :
                graph.getModules()) {

            Point position =
                    nodePositions.get(
                            module.getPackageName()
                    );

            if (position == null) {
                continue;
            }

            int x = position.x;
            int y = position.y;

            g.setColor(
                    module.isOpen()
                            ? JBColor.namedColor(
                            "Panel.infoForeground",
                            JBColor.foreground()
                    )
                            : JBColor.namedColor(
                            "Label.foreground",
                            JBColor.foreground()
                    )
            );

            g.fillRoundRect(
                    x,
                    y,
                    NODE_WIDTH,
                    NODE_HEIGHT,
                    16,
                    16
            );

            g.setColor(
                    JBColor.namedColor(
                            "EditorPane.background",
                            getBackground()
                    )
            );

            g.fillRoundRect(
                    x + 2,
                    y + 2,
                    NODE_WIDTH - 4,
                    NODE_HEIGHT - 4,
                    14,
                    14
            );

            paintModuleText(
                    g,
                    module,
                    x,
                    y
            );
        }
    }

    private void paintModuleText(
            @NotNull Graphics2D g,
            @NotNull ModulithModule module,
            int x,
            int y) {

        String moduleName =
                module.getName();

        String packageName =
                module.getPackageName();

        FontMetrics metrics =
                g.getFontMetrics();

        int nameWidth =
                metrics.stringWidth(moduleName);

        int nameX =
                x + (NODE_WIDTH - nameWidth) / 2;

        int nameY =
                y + 30;

        g.setColor(
                JBColor.foreground()
        );

        g.drawString(
                moduleName,
                nameX,
                nameY
        );

        String status =
                module.isOpen()
                        ? "open"
                        : "closed";

        int statusWidth =
                metrics.stringWidth(status);

        int statusX =
                x + (NODE_WIDTH - statusWidth) / 2;

        g.setColor(
                JBColor.GRAY
        );

        g.drawString(
                status,
                statusX,
                y + 50
        );

        String tooltip =
                moduleName +
                        " (" +
                        packageName +
                        ")";

        setToolTipText(
                tooltip
        );
    }

    private void paintDependencies(
            @NotNull Graphics2D g) {

        g.setStroke(
                new BasicStroke(
                        1.5f
                )
        );

        for (ModulithDependencyGraph.ModuleDependency dependency :
                graph.getDependencies()) {

            Point source =
                    nodePositions.get(
                            dependency.sourcePackage()
                    );

            Point target =
                    nodePositions.get(
                            dependency.targetPackage()
                    );

            if (source == null || target == null) {
                continue;
            }

            drawArrow(
                    g,
                    source,
                    target
            );
        }
    }

    private void drawArrow(
            @NotNull Graphics2D g,
            @NotNull Point source,
            @NotNull Point target) {

        Point sourceCenter =
                new Point(
                        source.x + NODE_WIDTH / 2,
                        source.y + NODE_HEIGHT / 2
                );

        Point targetCenter =
                new Point(
                        target.x + NODE_WIDTH / 2,
                        target.y + NODE_HEIGHT / 2
                );

        Point start =
                getIntersection(
                        sourceCenter,
                        targetCenter
                );

        Point end =
                getIntersection(
                        targetCenter,
                        sourceCenter
                );

        g.setColor(
                JBColor.GRAY
        );

        g.draw(
                new Line2D.Double(
                        start,
                        end
                )
        );

        drawArrowHead(
                g,
                start,
                end
        );
    }

    private void drawArrowHead(
            @NotNull Graphics2D g,
            @NotNull Point start,
            @NotNull Point end) {

        double angle =
                Math.atan2(
                        end.y - start.y,
                        end.x - start.x
                );

        int arrowSize = 9;

        double angle1 =
                angle + Math.PI * 0.82;

        double angle2 =
                angle - Math.PI * 0.82;

        int x1 =
                (int) (
                        end.x +
                                Math.cos(angle1) *
                                        arrowSize
                );

        int y1 =
                (int) (
                        end.y +
                                Math.sin(angle1) *
                                        arrowSize
                );

        int x2 =
                (int) (
                        end.x +
                                Math.cos(angle2) *
                                        arrowSize
                );

        int y2 =
                (int) (
                        end.y +
                                Math.sin(angle2) *
                                        arrowSize
                );

        g.drawLine(
                end.x,
                end.y,
                x1,
                y1
        );

        g.drawLine(
                end.x,
                end.y,
                x2,
                y2
        );
    }

    @NotNull
    private Point getIntersection(
            @NotNull Point rectangleCenter,
            @NotNull Point otherCenter) {

        double dx =
                otherCenter.x -
                        rectangleCenter.x;

        double dy =
                otherCenter.y -
                        rectangleCenter.y;

        if (dx == 0 && dy == 0) {
            return rectangleCenter;
        }

        double scaleX =
                (NODE_WIDTH / 2.0) /
                        Math.abs(dx);

        double scaleY =
                (NODE_HEIGHT / 2.0) /
                        Math.abs(dy);

        double scale =
                Math.min(
                        scaleX,
                        scaleY
                );

        return new Point(
                (int) (
                        rectangleCenter.x +
                                dx * scale
                ),
                (int) (
                        rectangleCenter.y +
                                dy * scale
                )
        );
    }

    private void calculateLayout() {

        nodePositions.clear();

        if (graph == null ||
                graph.getModules().isEmpty()) {

            return;
        }

        List<ModulithModule> modules =
                new ArrayList<>(
                        graph.getModules()
                );

        int columns =
                Math.max(
                        1,
                        (int) Math.ceil(
                                Math.sqrt(
                                        modules.size()
                                )
                        )
                );

        for (int i = 0;
             i < modules.size();
             i++) {

            ModulithModule module =
                    modules.get(i);

            int row =
                    i / columns;

            int column =
                    i % columns;

            int x =
                    PADDING +
                            column *
                                    (NODE_WIDTH +
                                            HORIZONTAL_GAP);

            int y =
                    PADDING +
                            row *
                                    (NODE_HEIGHT +
                                            VERTICAL_GAP);

            nodePositions.put(
                    module.getPackageName(),
                    new Point(x, y)
            );
        }

        int rows =
                (int) Math.ceil(
                        (double) modules.size()
                                / columns
                );

        int width =
                PADDING * 2 +
                        columns *
                                NODE_WIDTH +
                        (columns - 1) *
                                HORIZONTAL_GAP;

        int height =
                PADDING * 2 +
                        rows *
                                NODE_HEIGHT +
                        (rows - 1) *
                                VERTICAL_GAP;

        setPreferredSize(
                new java.awt.Dimension(
                        Math.max(width, 600),
                        Math.max(height, 400)
                )
        );
    }

    public void clearGraph() {

        graph = null;
        nodePositions.clear();

        revalidate();
        repaint();
    }
}