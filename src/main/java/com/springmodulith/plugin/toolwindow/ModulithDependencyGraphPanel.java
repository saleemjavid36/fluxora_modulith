package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
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

    private final Project project;
    private final Map<String, Point> nodePositions = new HashMap<>();

    private ModulithDependencyGraph graph;
    private ModulithModule selectedModule;
    private ModulithModule hoveredModule;
    private Consumer<ModulithModule> moduleSelectionListener;
    private Consumer<ModulithDependencyGraph.ModuleDependency> dependencySelectionListener;
    private ModulithDependencyGraph.ModuleDependency selectedDependency;

    public ModulithDependencyGraphPanel(@NotNull Project project) {
        this.project = project;
        setBackground(JBColor.background());
        setBorder(JBUI.Borders.empty(PADDING));
        setToolTipText("");

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                hoveredModule = findModuleAt(event.getPoint());
                ModulithDependencyGraph.ModuleDependency hoveredDependency =
                        hoveredModule == null ? findDependencyAt(event.getPoint()) : null;
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
                setCursor(Cursor.getDefaultCursor());
                setToolTipText("");
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                ModulithModule module = findModuleAt(event.getPoint());
                if (module != null) {
                    selectedDependency = null;
                    notifyDependencySelection(null);
                    selectedModule = module;
                    notifySelection(module);
                    repaint();
                    if (event.getClickCount() == 2) {
                        openModule(module);
                    }
                    return;
                }

                ModulithDependencyGraph.ModuleDependency dependency =
                        findDependencyAt(event.getPoint());
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
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
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
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintDependencies(g);
            paintModules(g);
            paintLegend(g);
        } finally {
            g.dispose();
        }
    }

    private void paintModules(@NotNull Graphics2D g) {
        for (ModulithModule module : graph.getModules()) {
            Point position = nodePositions.get(module.getPackageName());
            if (position == null) continue;

            boolean selected = module == selectedModule;
            boolean hovered = module == hoveredModule;
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
            if (source == null || target == null) continue;

            boolean highlighted = isDependencyRelatedToSelection(dependency)
                    || dependency == selectedDependency;
            boolean dimmed = selectedModule != null && !highlighted;
            boolean cyclic = graph.isCyclicEdge(dependency);

            g.setColor(edgeColor(dependency, dimmed, cyclic));
            g.setStroke(edgeStroke(dependency, highlighted, cyclic));
            drawArrow(g, source, target);
            drawEdgeLabel(g, dependency, source, target, dimmed);
        }
    }

    private java.awt.Color edgeColor(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            boolean dimmed,
            boolean cyclic) {
        if (dimmed) return JBColor.GRAY;
        if (dependency.isForbidden()) return JBColor.namedColor("ValidationError.foreground", JBColor.RED);
        if (cyclic) return JBColor.namedColor("Yellow.foreground", JBColor.ORANGE);
        if (dependency.isNamedInterface()) return JBColor.namedColor("Green.foreground", JBColor.GREEN);
        return JBColor.namedColor("Actions.Blue", JBColor.BLUE);
    }

    private BasicStroke edgeStroke(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency,
            boolean highlighted,
            boolean cyclic) {
        float width = highlighted ? 2.5f : 1.4f;
        if (cyclic) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f);
        }
        if (dependency.isNamedInterface()) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 5f}, 0f);
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
        if (dependency.isApiViolation()) {
            label = "API violation";
        } else if (dependency.isNamedInterface()) {
            label = dependency.namedInterface() == null
                    ? "named interface"
                    : ":: " + dependency.namedInterface();
        } else if (dependency.isForbidden()) {
            label = "forbidden";
        } else {
            label = dependency.referenceCount() > 1
                    ? "allowed ×" + dependency.referenceCount()
                    : "allowed";
        }

        Point a = center(source);
        Point b = center(target);
        int x = (a.x + b.x) / 2;
        int y = (a.y + b.y) / 2 - 5;

        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(label) + 10;
        int height = metrics.getHeight();
        g.setColor(JBColor.namedColor("ToolWindow.background", getBackground()));
        g.fillRoundRect(x - width / 2, y - height + 3, width, height, 8, 8);
        g.setColor(dimmed ? JBColor.GRAY : JBColor.foreground());
        g.drawString(label, x - metrics.stringWidth(label) / 2, y);
    }

    private void paintLegend(@NotNull Graphics2D g) {
        int y = Math.max(getHeight() - LEGEND_HEIGHT - 4, 10);
        int x = 12;
        drawLegendItem(g, x, y, "allowed", JBColor.namedColor("Actions.Blue", JBColor.BLUE), false);
        x += 90;
        drawLegendItem(g, x, y, "forbidden", JBColor.namedColor("ValidationError.foreground", JBColor.RED), false);
        x += 105;
        drawLegendItem(g, x, y, "named interface", JBColor.namedColor("Green.foreground", JBColor.GREEN), true);
        x += 135;
        drawLegendItem(g, x, y, "cycle", JBColor.namedColor("Yellow.foreground", JBColor.ORANGE), true);
    }

    private void drawLegendItem(@NotNull Graphics2D g, int x, int y, String text, java.awt.Color color, boolean dashed) {
        g.setColor(color);
        g.setStroke(dashed
                ? new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 5f}, 0f)
                : new BasicStroke(2f));
        g.drawLine(x, y, x + 24, y);
        g.setStroke(new BasicStroke(1f));
        g.setColor(JBColor.foreground());
        g.drawString(text, x + 30, y + 4);
    }

    private void drawCentered(@NotNull Graphics2D g, @NotNull String text, int x, int baseline, @NotNull FontMetrics metrics) {
        g.drawString(text, x + (NODE_WIDTH - metrics.stringWidth(text)) / 2, baseline);
    }

    private void drawArrow(@NotNull Graphics2D g, @NotNull Point source, @NotNull Point target) {
        Point sourceCenter = center(source);
        Point targetCenter = center(target);
        Point start = getIntersection(sourceCenter, targetCenter);
        Point end = getIntersection(targetCenter, sourceCenter);
        g.draw(new Line2D.Double(start, end));
        drawArrowHead(g, start, end);
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
            if (source == null || target == null) continue;

            Point start = edgeStart(dependency);
            Point end = edgeEnd(dependency);
            double distance = Line2D.ptSegDist(
                    start.x, start.y, end.x, end.y, point.x, point.y
            );

            if (distance <= 9.0 && distance < closestDistance) {
                closestDistance = distance;
                closest = dependency;
            }
        }

        return closest;
    }

    private void calculateLayout() {
        nodePositions.clear();
        if (graph == null || graph.getModules().isEmpty()) return;

        List<ModulithModule> modules = new ArrayList<>(graph.getModules());
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(modules.size())));
        for (int i = 0; i < modules.size(); i++) {
            int row = i / columns;
            int column = i % columns;
            nodePositions.put(
                    modules.get(i).getPackageName(),
                    new Point(
                            PADDING + column * (NODE_WIDTH + HORIZONTAL_GAP),
                            PADDING + row * (NODE_HEIGHT + VERTICAL_GAP)
                    )
            );
        }

        int rows = (int) Math.ceil((double) modules.size() / columns);
        int width = PADDING * 2 + columns * NODE_WIDTH + (columns - 1) * HORIZONTAL_GAP;
        int height = PADDING * 2 + rows * NODE_HEIGHT + (rows - 1) * VERTICAL_GAP + LEGEND_HEIGHT;
        setPreferredSize(new Dimension(Math.max(width, 650), Math.max(height, 450)));
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
        String interfaceName = dependency.namedInterface() == null
                ? ""
                : " :: " + dependency.namedInterface();
        setToolTipText(
                dependency.sourcePackage()
                        + " → "
                        + dependency.targetPackage()
                        + interfaceName
                        + " — "
                        + (dependency.isForbidden() ? "FORBIDDEN" : "ALLOWED")
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
        nodePositions.clear();
        notifySelection(null);
        notifyDependencySelection(null);
        revalidate();
        repaint();
    }
}
