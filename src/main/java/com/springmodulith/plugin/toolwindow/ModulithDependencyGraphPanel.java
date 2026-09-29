package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.geom.QuadCurve2D;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ModulithDependencyGraphPanel extends JPanel {

    private static final int NODE_WIDTH = 190;
    private static final int NODE_HEIGHT = 86;
    private static final int HORIZONTAL_GAP = 110;
    private static final int VERTICAL_GAP = 95;
    private static final int PADDING = 50;
    private static final int LEGEND_HEIGHT = 34;
    private static final int EMPTY_STATE_WIDTH = 520;
    private static final int EMPTY_STATE_HEIGHT = 180;
    private static final JBColor CYCLE_COLOR = new JBColor(
            new Color(0xC98A00),
            new Color(0xFFB020)
    );

    private static final JBColor CYCLE_LEGEND_BACKGROUND = new JBColor(
            new Color(0xFFF3CD),
            new Color(0x4A3900)
    );

    private static final int CYCLE_CURVE_OFFSET = 55;
    private static final double MIN_ZOOM = 0.60d;
    private static final double MAX_ZOOM = 2.00d;
    private static final double ZOOM_STEP = 0.10d;
    private static final int EDGE_ROUTE_OFFSET = 55;

    private final Project project;
    private final Map<String, Point> nodePositions = new HashMap<>();


    private ModulithDependencyGraph graph;
    private ModulithModule selectedModule;
    private ModulithModule hoveredModule;
    private Consumer<ModulithModule> moduleSelectionListener;
    private Consumer<ModulithDependencyGraph.ModuleDependency> dependencySelectionListener;
    private ModulithDependencyGraph.ModuleDependency selectedDependency;
    private ModulithModule draggedModule;
    private Point dragOffset;
    private boolean draggingModule;
    private double zoom = 1.0d;
    private int baseGraphWidth = 650;
    private int baseGraphHeight = 450;

    public ModulithDependencyGraphPanel(@NotNull Project project) {
        this.project = project;
        setBackground(JBColor.background());
        setBorder(JBUI.Borders.empty(PADDING));
        setToolTipText("");

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                Point worldPoint = toWorldPoint(event.getPoint());
                hoveredModule = findModuleAt(worldPoint);
                ModulithDependencyGraph.ModuleDependency hoveredDependency =
                        hoveredModule == null ? findDependencyAt(worldPoint) : null;

                setCursor(hoveredModule != null || hoveredDependency != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());

                if (hoveredModule != null) {
                    updateTooltip(hoveredModule);
                } else if (hoveredDependency != null) {
                    updateTooltip(hoveredDependency);
                } else {
                    setToolTipText("");
                }
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredModule = null;
                if (!draggingModule) {
                    setCursor(Cursor.getDefaultCursor());
                }
                setToolTipText("");
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                Point worldPoint = toWorldPoint(event.getPoint());
                ModulithModule module = findModuleAt(worldPoint);

                if (module != null && event.getButton() == MouseEvent.BUTTON1) {
                    selectedDependency = null;
                    notifyDependencySelection(null);
                    selectedModule = module;
                    notifySelection(module);

                    Point position = nodePositions.get(module.getPackageName());
                    if (position != null) {
                        draggedModule = module;
                        dragOffset = new Point(
                                worldPoint.x - position.x,
                                worldPoint.y - position.y
                        );
                        draggingModule = false;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }

                    repaint();
                    return;
                }

                if (event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                ModulithDependencyGraph.ModuleDependency dependency =
                        findDependencyAt(worldPoint);
                if (dependency != null) {
                    selectedModule = null;
                    selectedDependency = dependency;
                    notifySelection(null);
                    notifyDependencySelection(dependency);
                    repaint();
                    if (event.getClickCount() == 2) {
                        openFirstReference(dependency);
                    }
                    return;
                }

                clearSelection();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (draggedModule == null
                        || !SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }

                Point worldPoint = toWorldPoint(event.getPoint());
                Point position = nodePositions.get(draggedModule.getPackageName());

                if (position == null || dragOffset == null) {
                    return;
                }

                int newX = Math.max(PADDING / 2, worldPoint.x - dragOffset.x);
                int newY = Math.max(PADDING / 2, worldPoint.y - dragOffset.y);

                if (position.x != newX || position.y != newY) {
                    position.setLocation(newX, newY);
                    draggingModule = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    updatePreferredSizeForNodes();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (draggedModule != null) {
                    ModulithModule releasedModule = draggedModule;
                    boolean wasDragged = draggingModule;

                    draggedModule = null;
                    dragOffset = null;
                    draggingModule = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                    if (!wasDragged
                            && event.getButton() == MouseEvent.BUTTON1
                            && event.getClickCount() == 2) {
                        openModule(releasedModule);
                    }
                }
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent event) {
                if (event.isShiftDown()) {
                    scrollHorizontally(event);
                    return;
                }

                if (event.isControlDown()) {
                    if (event.getWheelRotation() < 0) {
                        zoomIn();
                    } else if (event.getWheelRotation() > 0) {
                        zoomOut();
                    }

                    event.consume();
                    return;
                }

                scrollVertically(event);
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);
    }

    private void scrollHorizontally(@NotNull java.awt.event.MouseWheelEvent event) {
        javax.swing.JScrollPane scrollPane =
                (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(
                        javax.swing.JScrollPane.class,
                        this
                );

        if (scrollPane == null) {
            return;
        }

        javax.swing.JScrollBar horizontalBar =
                scrollPane.getHorizontalScrollBar();

        if (!horizontalBar.isVisible()) {
            return;
        }

        int direction = event.getWheelRotation();
        int unit = Math.max(24, horizontalBar.getUnitIncrement());
        int delta = direction * unit * Math.max(1, event.getScrollAmount());
        int maximum = horizontalBar.getMaximum() - horizontalBar.getVisibleAmount();
        int value = Math.max(0, Math.min(maximum, horizontalBar.getValue() + delta));

        horizontalBar.setValue(value);
        event.consume();
    }

    private void scrollVertically(@NotNull java.awt.event.MouseWheelEvent event) {
        javax.swing.JScrollPane scrollPane =
                (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(
                        javax.swing.JScrollPane.class,
                        this
                );

        if (scrollPane == null) {
            return;
        }

        javax.swing.JScrollBar verticalBar =
                scrollPane.getVerticalScrollBar();

        int direction = event.getWheelRotation();
        int unit = Math.max(24, verticalBar.getUnitIncrement());
        int delta = direction * unit * Math.max(1, event.getScrollAmount());
        int maximum = verticalBar.getMaximum() - verticalBar.getVisibleAmount();
        int value = Math.max(
                0,
                Math.min(maximum, verticalBar.getValue() + delta)
        );

        if (value != verticalBar.getValue()) {
            verticalBar.setValue(value);
        }

        event.consume();
    }

    public void setModuleSelectionListener(Consumer<ModulithModule> listener) {
        this.moduleSelectionListener = listener;
    }

    public void setDependencySelectionListener(
            Consumer<ModulithDependencyGraph.ModuleDependency> listener) {
        this.dependencySelectionListener = listener;
    }

    public void setGraph(@NotNull ModulithDependencyGraph graph) {
        this.graph = graph;
        this.selectedModule = null;
        this.selectedDependency = null;
        this.hoveredModule = null;
        calculateLayout();
        revalidate();
        repaint();
        notifySelection(null);
        notifyDependencySelection(null);
    }

    private void notifySelection(ModulithModule module) {
        if (moduleSelectionListener != null) {
            moduleSelectionListener.accept(module);
        }
    }

    private void notifyDependencySelection(
            ModulithDependencyGraph.ModuleDependency dependency) {
        if (dependencySelectionListener != null) {
            dependencySelectionListener.accept(dependency);
        }
    }

    @Override
    protected void paintComponent(@NotNull Graphics graphics) {
        super.paintComponent(graphics);
        if (graph == null) return;

        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            if (graph.getModules().isEmpty()) {
                paintEmptyState(g);
                return;
            }

            java.awt.geom.AffineTransform originalTransform = g.getTransform();
            g.scale(zoom, zoom);
            paintDependencies(g);
            paintModules(g);
            g.setTransform(originalTransform);
            paintLegend(g);
        } finally {
            g.dispose();
        }
    }
    private void paintEmptyState(@NotNull Graphics2D g) {
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        int cardX = centerX - EMPTY_STATE_WIDTH / 2;
        int cardY = centerY - EMPTY_STATE_HEIGHT / 2;

        g.setColor(JBColor.namedColor(
                "Panel.background",
                getBackground()
        ));
        g.fillRoundRect(
                cardX,
                cardY,
                EMPTY_STATE_WIDTH,
                EMPTY_STATE_HEIGHT,
                16,
                16
        );

        g.setColor(JBColor.namedColor(
                "Borders.color",
                JBColor.GRAY
        ));
        g.drawRoundRect(
                cardX,
                cardY,
                EMPTY_STATE_WIDTH,
                EMPTY_STATE_HEIGHT,
                16,
                16
        );

        Font originalFont = g.getFont();

        g.setColor(JBColor.foreground());
        g.setFont(originalFont.deriveFont(Font.BOLD, 16.0f));

        String title = "No Spring Modulith modules found";
        FontMetrics metrics = g.getFontMetrics();

        g.drawString(
                title,
                centerX - metrics.stringWidth(title) / 2,
                centerY - 20
        );

        g.setFont(originalFont.deriveFont(Font.PLAIN, 13.0f));
        g.setColor(JBColor.namedColor(
                "Label.secondaryForeground",
                JBColor.GRAY
        ));

        String message =
                "No recognized application modules were detected in this project.";

        metrics = g.getFontMetrics();

        g.drawString(
                message,
                centerX - metrics.stringWidth(message) / 2,
                centerY + 8
        );

        String hint =
                "Add @ApplicationModule or configure module detection to populate the graph.";

        metrics = g.getFontMetrics();

        g.drawString(
                hint,
                centerX - metrics.stringWidth(hint) / 2,
                centerY + 32
        );

        g.setFont(originalFont);
    }

    private void paintModules(@NotNull Graphics2D g) {
        for (ModulithModule module : graph.getModules()) {
            Point position = nodePositions.get(module.getPackageName());
            if (position == null) continue;

            boolean selected = module == selectedModule;
            boolean hovered = selectedModule == null
                    && selectedDependency == null
                    && module == hoveredModule;
            boolean dimmed = selectedModule != null && !isRelatedToSelection(module);

            int borderThickness = selected ? 3 : hovered ? 2 : 1;
            g.setStroke(new BasicStroke(borderThickness));
            g.setColor(dimmed
                    ? JBColor.GRAY
                    : selected
                    ? JBColor.namedColor("Actions.Blue", JBColor.BLUE)
                    : module.isOpen()
                    ? JBColor.namedColor("Panel.infoForeground", JBColor.foreground())
                    : JBColor.namedColor("Label.foreground", JBColor.foreground()));

            int x = position.x;
            int y = position.y;
            g.drawRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 14, 14);

            FontMetrics metrics = g.getFontMetrics();
            String name = module.getName();
            String status = module.isOpen() ? "OPEN" : "CLOSED";
            String rule = module.isAllowedDependenciesConfigured() ? "explicit rules" : "implicit dependencies";

            g.setFont(g.getFont().deriveFont(Font.BOLD));
            drawCentered(g, name, x, y + 28, metrics);
            g.setFont(g.getFont().deriveFont(Font.PLAIN));
            drawCentered(g, status, x, y + 48, metrics);
            g.setColor(JBColor.GRAY);
            drawCentered(g, rule, x, y + 68, metrics);
        }
    }

    private void paintDependencies(@NotNull Graphics2D g) {
        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            Point source = nodePositions.get(dependency.sourcePackage());
            Point target = nodePositions.get(dependency.targetPackage());

            if (source == null || target == null) {
                continue;
            }

            boolean highlighted = dependency == selectedDependency;

            boolean dimmed = selectedModule != null && !highlighted;
            boolean cyclic = graph.isCyclicEdge(dependency);

            g.setColor(edgeColor(dependency, dimmed, cyclic));
            g.setStroke(edgeStroke(dependency, highlighted, cyclic));

            if (cyclic) {
                drawCycleArrow(g, dependency);
            } else {
                drawArrow(g, dependency, source, target);
            }

            drawEdgeLabel(g, dependency, source, target, dimmed);
        }
    }
    private void drawCycleArrow(
            @NotNull Graphics2D g,
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {

        Point start = edgeStart(dependency);
        Point end = edgeEnd(dependency);

        Point control = getCycleControlPoint(dependency, start, end);

        QuadCurve2D curve = new QuadCurve2D.Double(
                start.x,
                start.y,
                control.x,
                control.y,
                end.x,
                end.y
        );

        g.draw(curve);

        // Use the tangent near the end of the curve for the arrow head.
        drawArrowHead(g, control, end);
    }
    @NotNull
    private Point getCycleControlPoint(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            @NotNull Point start,
            @NotNull Point end) {

        double dx = end.x - start.x;
        double dy = end.y - start.y;

        double length = Math.sqrt(dx * dx + dy * dy);

        if (length == 0) {
            return new Point(
                    (start.x + end.x) / 2,
                    (start.y + end.y) / 2
            );
        }

        // Perpendicular vector.
        double normalX = -dy / length;
        double normalY = dx / length;

        /*
         * Opposite directions use opposite sides of the connection.
         *
         * billing -> payment
         * payment -> billing
         *
         * therefore the two arrows don't overlap.
         */
        int direction = dependency.sourcePackage()
                .compareTo(dependency.targetPackage()) < 0 ? 1 : -1;

        double offset = Math.max(
                CYCLE_CURVE_OFFSET,
                Math.min(80, length * 0.25)
        );

        double centerX = (start.x + end.x) / 2.0;
        double centerY = (start.y + end.y) / 2.0;

        return new Point(
                (int) Math.round(centerX + normalX * offset * direction),
                (int) Math.round(centerY + normalY * offset * direction)
        );
    }

    private java.awt.Color edgeColor(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            boolean dimmed,
            boolean cyclic) {

        if (dimmed) {
            return JBColor.GRAY;
        }

        if (cyclic) {
            return CYCLE_COLOR;
        }

        if (dependency.isForbidden()) {
            return JBColor.namedColor(
                    "ValidationError.foreground",
                    JBColor.RED
            );
        }

        if (dependency.isNamedInterface()) {
            return JBColor.namedColor(
                    "Green.foreground",
                    JBColor.GREEN
            );
        }

        return JBColor.namedColor(
                "Actions.Blue",
                JBColor.BLUE
        );
    }

    private BasicStroke edgeStroke(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            boolean highlighted,
            boolean cyclic) {

        float width = highlighted ? 3.0f : 2.2f;

        if (cyclic) {
            return new BasicStroke(
                    width,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    10f,
                    new float[]{10f, 7f},
                    0f
            );
        }

        if (dependency.isNamedInterface()) {
            return new BasicStroke(
                    width,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    10f,
                    new float[]{5f, 5f},
                    0f
            );
        }

        return new BasicStroke(width);
    }

    private void drawEdgeLabel(
            @NotNull Graphics2D g,
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            @NotNull Point source,
            @NotNull Point target,
            boolean dimmed) {
        String label;

        if (graph.isCyclicEdge(dependency)) {
            label = "cycle";
        } else {
            label = dependencyEdgeLabel(dependency);
        }

        Point a = edgeStart(dependency);
        Point b = edgeEnd(dependency);

        int x;
        int y;

        if (graph.isCyclicEdge(dependency)) {
            Point control = getCycleControlPoint(dependency, a, b);
            Point midpoint = quadraticPoint(a, control, b, 0.5d);
            x = midpoint.x;
            y = midpoint.y - 5;
        } else {
            Point control = getRouteControlPoint(
                    dependency,
                    a,
                    b
            );

            if (control == null) {
                x = (a.x + b.x) / 2;
                y = (a.y + b.y) / 2 - 5;
            } else {
                Point midpoint =
                        quadraticPoint(a, control, b, 0.5d);
                x = midpoint.x;
                y = midpoint.y - 5;
            }
        }

        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(label) + 10;
        int height = metrics.getHeight();
        g.setColor(JBColor.namedColor("ToolWindow.background", getBackground()));
        g.fillRoundRect(x - width / 2, y - height + 3, width, height, 8, 8);
        g.setColor(dimmed ? JBColor.GRAY : JBColor.foreground());
        g.drawString(label, x - metrics.stringWidth(label) / 2, y);
    }

    @NotNull
    private String dependencyEdgeLabel(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {

        if (dependency.isMixed()) {
            List<String> parts = new ArrayList<>();

            if (dependency.forbiddenReferenceCount() > 0 || dependency.isApiViolation()) {
                int count = dependency.forbiddenReferenceCount();
                parts.add(count > 0 ? "forbidden ×" + count : "API violation");
            }

            if (dependency.namedInterfaceReferenceCount() > 0) {
                parts.add("allowed (named interface) ×" + dependency.namedInterfaceReferenceCount());
            }

            if (dependency.allowedReferenceCount() > 0) {
                parts.add("root API ×" + dependency.allowedReferenceCount());
            }

            return String.join(" • ", parts);
        }

        if (dependency.isApiViolation()) {
            return "API violation";
        }

        if (dependency.isNamedInterface()) {
            if (dependency.namedInterface() != null) {
                return dependency.namedInterfaceReferenceCount() > 1
                        ? ":: " + dependency.namedInterface() + " ×" + dependency.namedInterfaceReferenceCount()
                        : ":: " + dependency.namedInterface();
            }
            return "named interface ×" + dependency.namedInterfaceReferenceCount();
        }

        if (dependency.isForbidden()) {
            return dependency.forbiddenReferenceCount() > 1
                    ? "forbidden ×" + dependency.forbiddenReferenceCount()
                    : "forbidden";
        }

        return dependency.allowedReferenceCount() > 1
                ? "root API ×" + dependency.allowedReferenceCount()
                : "root API";
    }

    private void paintLegend(@NotNull Graphics2D g) {
        int y = Math.max(getHeight() - LEGEND_HEIGHT - 4, 10);
        int x = 12;

        drawLegendItem(
                g,
                x,
                y,
                "allowed",
                JBColor.namedColor("Actions.Blue", JBColor.BLUE),
                LegendStyle.SOLID,
                false
        );

        x += 90;

        drawLegendItem(
                g,
                x,
                y,
                "forbidden",
                JBColor.namedColor(
                        "ValidationError.foreground",
                        JBColor.RED
                ),
                LegendStyle.SOLID,
                false
        );

        x += 105;

        drawLegendItem(
                g,
                x,
                y,
                "named interface",
                JBColor.namedColor(
                        "Green.foreground",
                        JBColor.GREEN
                ),
                LegendStyle.DOTTED,
                false
        );

        x += 135;

        boolean cycleDetected = !graph.getCycles().isEmpty();

        drawLegendItem(
                g,
                x,
                y,
                "cycle",
                CYCLE_COLOR,
                LegendStyle.DASHED,
                cycleDetected
        );
    }

    private void drawLegendItem(
            @NotNull Graphics2D g,
            int x,
            int y,
            @NotNull String text,
            @NotNull java.awt.Color color,
            @NotNull LegendStyle style,
            boolean highlighted) {

        Font originalFont = g.getFont();
        FontMetrics metrics = g.getFontMetrics();

        int textWidth = metrics.stringWidth(text);

        if (highlighted) {
            int backgroundWidth = textWidth + 46;

            g.setColor(CYCLE_LEGEND_BACKGROUND);
            g.fillRoundRect(
                    x - 8,
                    y - 16,
                    backgroundWidth,
                    28,
                    10,
                    10
            );
        }

        g.setColor(color);

        float width = highlighted ? 3.2f : 2.2f;

        switch (style) {
            case SOLID -> {
                g.setStroke(new BasicStroke(
                        width,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));
                g.drawLine(x, y, x + 24, y);
            }

            case DOTTED -> {
                g.setStroke(new BasicStroke(
                        width,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        10f,
                        new float[]{1f, 6f},
                        0f
                ));
                g.drawLine(x, y, x + 24, y);
            }

            case DASHED -> {
                g.setStroke(new BasicStroke(
                        width,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        10f,
                        new float[]{9f, 5f},
                        0f
                ));
                g.drawLine(x, y, x + 24, y);
            }

            case DASH_DOT -> {
                g.setStroke(new BasicStroke(
                        width,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        10f,
                        new float[]{9f, 4f, 2f, 4f},
                        0f
                ));
                g.drawLine(x, y, x + 24, y);
            }
        }

        g.setStroke(new BasicStroke(1f));

        g.setColor(highlighted
                ? color
                : JBColor.foreground());

        g.setFont(
                highlighted
                        ? originalFont.deriveFont(Font.BOLD)
                        : originalFont
        );

        g.drawString(
                text,
                x + 32,
                y + 4
        );

        g.setFont(originalFont);
    }

    private enum LegendStyle {
        SOLID,
        DOTTED,
        DASHED,
        DASH_DOT
    }

    private void drawCentered(@NotNull Graphics2D g, @NotNull String text, int x, int baseline, @NotNull FontMetrics metrics) {
        g.drawString(text, x + (NODE_WIDTH - metrics.stringWidth(text)) / 2, baseline);
    }

    private void drawArrow(
            @NotNull Graphics2D g,
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            @NotNull Point source,
            @NotNull Point target) {

        Point sourceCenter = center(source);
        Point targetCenter = center(target);
        Point start = getIntersection(sourceCenter, targetCenter);
        Point end = getIntersection(targetCenter, sourceCenter);

        Point control = getRouteControlPoint(dependency, start, end);
        if (control == null) {
            g.draw(new Line2D.Double(start, end));
            drawArrowHead(g, start, end);
            return;
        }

        QuadCurve2D curve = new QuadCurve2D.Double(
                start.x,
                start.y,
                control.x,
                control.y,
                end.x,
                end.y
        );
        g.draw(curve);

        Point tangentStart = quadraticPoint(start, control, end, 0.90d);
        drawArrowHead(g, tangentStart, end);
    }

    private Point getRouteControlPoint(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            @NotNull Point start,
            @NotNull Point end) {

        Point parallelControl = getParallelControlPoint(dependency, start, end);
        if (parallelControl != null) {
            return parallelControl;
        }

        if (!edgeIsBlocked(start, end, dependency)) {
            return null;
        }

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length == 0) {
            return null;
        }

        double normalX = -dy / length;
        double normalY = dx / length;

        Point positive = new Point(
                (int) Math.round(
                        (start.x + end.x) / 2.0d
                                + normalX * EDGE_ROUTE_OFFSET
                ),
                (int) Math.round(
                        (start.y + end.y) / 2.0d
                                + normalY * EDGE_ROUTE_OFFSET
                )
        );

        Point negative = new Point(
                (int) Math.round(
                        (start.x + end.x) / 2.0d
                                - normalX * EDGE_ROUTE_OFFSET
                ),
                (int) Math.round(
                        (start.y + end.y) / 2.0d
                                - normalY * EDGE_ROUTE_OFFSET
                )
        );

        int positiveScore =
                routeBlockingScore(start, positive, end, dependency);
        int negativeScore =
                routeBlockingScore(start, negative, end, dependency);

        return positiveScore <= negativeScore
                ? positive
                : negative;
    }

    @Nullable
    private Point getParallelControlPoint(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            @NotNull Point start,
            @NotNull Point end) {

        List<ModulithDependencyGraph.ModuleDependency> parallel = new ArrayList<>();
        for (ModulithDependencyGraph.ModuleDependency candidate : graph.getDependencies()) {
            if (candidate.sourcePackage().equals(dependency.sourcePackage())
                    && candidate.targetPackage().equals(dependency.targetPackage())) {
                parallel.add(candidate);
            }
        }

        if (parallel.size() <= 1) {
            return null;
        }

        int index = parallel.indexOf(dependency);
        if (index < 0) {
            return null;
        }

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) {
            return null;
        }

        double normalX = -dy / length;
        double normalY = dx / length;
        double offset = (index - (parallel.size() - 1) / 2.0d) * EDGE_ROUTE_OFFSET;

        return new Point(
                (int) Math.round((start.x + end.x) / 2.0d + normalX * offset),
                (int) Math.round((start.y + end.y) / 2.0d + normalY * offset)
        );
    }

    private boolean edgeIsBlocked(
            @NotNull Point start,
            @NotNull Point end,
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {

        for (ModulithModule module : graph.getModules()) {
            String packageName = module.getPackageName();

            if (packageName.equals(dependency.sourcePackage())
                    || packageName.equals(dependency.targetPackage())) {
                continue;
            }

            Point position = nodePositions.get(packageName);
            if (position == null) {
                continue;
            }

            java.awt.Rectangle rectangle =
                    new java.awt.Rectangle(
                            position.x - 4,
                            position.y - 4,
                            NODE_WIDTH + 8,
                            NODE_HEIGHT + 8
                    );

            if (rectangle.intersectsLine(
                    start.x,
                    start.y,
                    end.x,
                    end.y)) {
                return true;
            }
        }

        return false;
    }

    private int routeBlockingScore(
            @NotNull Point start,
            @NotNull Point control,
            @NotNull Point end,
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {

        int score = 0;

        for (ModulithModule module : graph.getModules()) {
            String packageName = module.getPackageName();

            if (packageName.equals(dependency.sourcePackage())
                    || packageName.equals(dependency.targetPackage())) {
                continue;
            }

            Point position = nodePositions.get(packageName);
            if (position == null) {
                continue;
            }

            java.awt.Rectangle rectangle =
                    new java.awt.Rectangle(
                            position.x - 4,
                            position.y - 4,
                            NODE_WIDTH + 8,
                            NODE_HEIGHT + 8
                    );

            if (quadraticIntersectsRectangle(
                    start,
                    control,
                    end,
                    rectangle)) {
                score++;
            }
        }

        return score;
    }

    private boolean quadraticIntersectsRectangle(
            @NotNull Point start,
            @NotNull Point control,
            @NotNull Point end,
            @NotNull java.awt.Rectangle rectangle) {

        Point previous = start;

        for (int i = 1; i <= 24; i++) {
            double t = i / 24.0d;
            Point current = quadraticPoint(
                    start,
                    control,
                    end,
                    t
            );

            if (rectangle.intersectsLine(
                    previous.x,
                    previous.y,
                    current.x,
                    current.y)) {
                return true;
            }

            previous = current;
        }

        return false;
    }

    @NotNull
    private Point quadraticPoint(
            @NotNull Point start,
            @NotNull Point control,
            @NotNull Point end,
            double t) {

        double oneMinusT = 1.0d - t;

        return new Point(
                (int) Math.round(
                        oneMinusT * oneMinusT * start.x
                                + 2.0d * oneMinusT * t * control.x
                                + t * t * end.x
                ),
                (int) Math.round(
                        oneMinusT * oneMinusT * start.y
                                + 2.0d * oneMinusT * t * control.y
                                + t * t * end.y
                )
        );
    }

    private void drawArrowHead(@NotNull Graphics2D g, @NotNull Point start, @NotNull Point end) {
        double angle = Math.atan2(end.y - start.y, end.x - start.x);
        int arrowSize = 9;
        double angle1 = angle + Math.PI * 0.82;
        double angle2 = angle - Math.PI * 0.82;
        int x1 = (int) (end.x + Math.cos(angle1) * arrowSize);
        int y1 = (int) (end.y + Math.sin(angle1) * arrowSize);
        int x2 = (int) (end.x + Math.cos(angle2) * arrowSize);
        int y2 = (int) (end.y + Math.sin(angle2) * arrowSize);
        g.drawLine(end.x, end.y, x1, y1);
        g.drawLine(end.x, end.y, x2, y2);
    }

    @NotNull
    private Point center(@NotNull Point position) {
        return new Point(position.x + NODE_WIDTH / 2, position.y + NODE_HEIGHT / 2);
    }

    @NotNull
    private Point getIntersection(@NotNull Point rectangleCenter, @NotNull Point otherCenter) {
        double dx = otherCenter.x - rectangleCenter.x;
        double dy = otherCenter.y - rectangleCenter.y;
        if (dx == 0 && dy == 0) return rectangleCenter;
        double scaleX = (NODE_WIDTH / 2.0) / Math.abs(dx);
        double scaleY = (NODE_HEIGHT / 2.0) / Math.abs(dy);
        double scale = Math.min(scaleX, scaleY);
        return new Point(
                (int) (rectangleCenter.x + dx * scale),
                (int) (rectangleCenter.y + dy * scale)
        );
    }

    @NotNull
    private Point edgeStart(@NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        return getIntersection(
                center(nodePositions.get(dependency.sourcePackage())),
                center(nodePositions.get(dependency.targetPackage()))
        );
    }

    @NotNull
    private Point edgeEnd(@NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        return getIntersection(
                center(nodePositions.get(dependency.targetPackage())),
                center(nodePositions.get(dependency.sourcePackage()))
        );
    }

    private ModulithDependencyGraph.ModuleDependency findDependencyAt(@NotNull Point point) {
        if (graph == null) return null;

        ModulithDependencyGraph.ModuleDependency closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            Point source = nodePositions.get(dependency.sourcePackage());
            Point target = nodePositions.get(dependency.targetPackage());

            if (source == null || target == null) {
                continue;
            }

            Point start = edgeStart(dependency);
            Point end = edgeEnd(dependency);
            double distance;

            if (graph.isCyclicEdge(dependency)) {
                Point control = getCycleControlPoint(
                        dependency,
                        start,
                        end
                );

                distance = quadraticDistanceToPoint(
                        start,
                        control,
                        end,
                        point
                );
            } else {
                Point control = getRouteControlPoint(
                        dependency,
                        start,
                        end
                );

                distance = control == null
                        ? Line2D.ptSegDist(
                        start.x,
                        start.y,
                        end.x,
                        end.y,
                        point.x,
                        point.y
                )
                        : quadraticDistanceToPoint(
                        start,
                        control,
                        end,
                        point
                );
            }

            if (distance <= 10.0 && distance < closestDistance) {
                closestDistance = distance;
                closest = dependency;
            }
        }

        return closest;
    }

    private double quadraticDistanceToPoint(
            @NotNull Point start,
            @NotNull Point control,
            @NotNull Point end,
            @NotNull Point point) {

        double closestDistance = Double.MAX_VALUE;
        Point previous = start;

        for (int i = 1; i <= 32; i++) {
            double t = i / 32.0d;
            Point current = quadraticPoint(
                    start,
                    control,
                    end,
                    t
            );

            closestDistance = Math.min(
                    closestDistance,
                    Line2D.ptSegDist(
                            previous.x,
                            previous.y,
                            current.x,
                            current.y,
                            point.x,
                            point.y
                    )
            );

            previous = current;
        }

        return closestDistance;
    }

    private void calculateLayout() {
        nodePositions.clear();

        if (graph == null) {
            return;
        }

        if (graph.getModules().isEmpty()) {
            baseGraphWidth =
                    Math.max(650, EMPTY_STATE_WIDTH + PADDING * 2);
            baseGraphHeight = 450;
            updatePreferredSize();
            return;
        }

        List<ModulithModule> modules =
                new ArrayList<>(graph.getModules());

        int columns = Math.max(
                1,
                (int) Math.ceil(Math.sqrt(modules.size()))
        );

        for (int i = 0; i < modules.size(); i++) {
            int row = i / columns;
            int column = i % columns;

            nodePositions.put(
                    modules.get(i).getPackageName(),
                    new Point(
                            PADDING
                                    + column * (NODE_WIDTH + HORIZONTAL_GAP),
                            PADDING
                                    + row * (NODE_HEIGHT + VERTICAL_GAP)
                    )
            );
        }

        int rows =
                (int) Math.ceil((double) modules.size() / columns);

        baseGraphWidth = Math.max(
                650,
                PADDING * 2
                        + columns * NODE_WIDTH
                        + (columns - 1) * HORIZONTAL_GAP
        );

        baseGraphHeight = Math.max(
                450,
                PADDING * 2
                        + rows * NODE_HEIGHT
                        + (rows - 1) * VERTICAL_GAP
                        + LEGEND_HEIGHT
        );

        updatePreferredSize();
    }

    private void updatePreferredSizeForNodes() {
        int maxX = PADDING;
        int maxY = PADDING;

        for (Point position : nodePositions.values()) {
            maxX = Math.max(
                    maxX,
                    position.x + NODE_WIDTH + PADDING
            );
            maxY = Math.max(
                    maxY,
                    position.y + NODE_HEIGHT + PADDING
            );
        }

        baseGraphWidth = Math.max(650, maxX);
        baseGraphHeight = Math.max(
                450,
                maxY + LEGEND_HEIGHT
        );

        updatePreferredSize();
    }

    private void updatePreferredSize() {
        setPreferredSize(new Dimension(
                Math.max(
                        650,
                        (int) Math.ceil(baseGraphWidth * zoom)
                ),
                Math.max(
                        450,
                        (int) Math.ceil(baseGraphHeight * zoom)
                )
        ));
        revalidate();
    }

    @NotNull
    private Point toWorldPoint(@NotNull Point screenPoint) {
        return new Point(
                (int) Math.round(screenPoint.x / zoom),
                (int) Math.round(screenPoint.y / zoom)
        );
    }

    public void zoomIn() {
        setZoom(Math.min(MAX_ZOOM, zoom + ZOOM_STEP));
    }

    public void zoomOut() {
        setZoom(Math.max(MIN_ZOOM, zoom - ZOOM_STEP));
    }

    public void resetZoom() {
        setZoom(1.0d);
    }

    public double getZoom() {
        return zoom;
    }

    private void setZoom(double newZoom) {
        double clamped =
                Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));

        if (Math.abs(clamped - zoom) < 0.001d) {
            return;
        }

        zoom = clamped;
        updatePreferredSize();
        repaint();
    }

    private ModulithModule findModuleAt(@NotNull Point point) {
        if (graph == null) return null;
        for (ModulithModule module : graph.getModules()) {
            Point position = nodePositions.get(module.getPackageName());
            if (position != null
                    && new java.awt.Rectangle(position.x, position.y, NODE_WIDTH, NODE_HEIGHT).contains(point)) {
                return module;
            }
        }
        return null;
    }

    private boolean isRelatedToSelection(@NotNull ModulithModule module) {
        if (selectedModule == null || module == selectedModule) return true;
        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            if (dependency.sourcePackage().equals(selectedModule.getPackageName())
                    && dependency.targetPackage().equals(module.getPackageName())) return true;
            if (dependency.targetPackage().equals(selectedModule.getPackageName())
                    && dependency.sourcePackage().equals(module.getPackageName())) return true;
        }
        return false;
    }

    private boolean isDependencyRelatedToSelection(@NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        if (selectedModule == null) return false;
        String selectedPackage = selectedModule.getPackageName();
        return dependency.sourcePackage().equals(selectedPackage)
                || dependency.targetPackage().equals(selectedPackage);
    }

    private void updateTooltip(ModulithModule module) {
        if (module == null) {
            setToolTipText("");
            return;
        }
        setToolTipText(module.getName() + " (" + module.getPackageName() + ") — "
                + (module.isOpen() ? "OPEN" : "CLOSED")
                + (module.isAllowedDependenciesConfigured() ? ", explicit dependencies" : ", implicit dependencies"));
    }

    private void updateTooltip(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        String interfaces = dependency.namedInterfaces().isEmpty()
                ? ""
                : " — interfaces: " + String.join(", ", dependency.namedInterfaces());
        setToolTipText(
                dependency.sourcePackage()
                        + " → "
                        + dependency.targetPackage()
                        + " — "
                        + dependencyEdgeLabel(dependency)
                        + interfaces
                        + " — "
                        + dependency.referenceCount()
                        + " reference(s)"
        );
    }

    private void openFirstReference(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        if (dependency.references().isEmpty()) return;
        ModulithModuleNavigation.openReference(
                project,
                dependency.references().get(0)
        );
    }

    private void openModule(@NotNull ModulithModule module) {
        ModulithModuleNavigation.openPackage(project, module);
    }

    public ModulithModule getSelectedModule() {
        return selectedModule;
    }

    public void clearSelection() {
        selectedModule = null;
        selectedDependency = null;
        notifySelection(null);
        notifyDependencySelection(null);
        repaint();
    }

    public void clearGraph() {
        graph = null;
        selectedModule = null;
        selectedDependency = null;
        hoveredModule = null;
        draggedModule = null;
        dragOffset = null;
        draggingModule = false;
        nodePositions.clear();
        notifySelection(null);
        notifyDependencySelection(null);
        revalidate();
        repaint();
    }
}
