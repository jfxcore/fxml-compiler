// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.BindingContextSelector;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.text.AttachedSegmentNode;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sorts the property initializers of an object according to the expressions that read them.
 * References can occur directly in a property or inside an object contained by that property. For example,
 * a binding in a child node can require an assignment on its parent to run before the parent's {@code children}
 * initializer. In that case, the child dependency is lifted to the containing {@code children} property.
 */
public final class PropertyAssignmentSorter {

    private enum DependencyKind {
        HARD,
        SOFT
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private record Dependency(
        PropertyNode property,
        SourceInfo sourceInfo,
        DependencyKind kind) {}

    private final ObjectNode objectNode;
    private final TypeInstance objectType;
    private final List<PropertyNode> properties;
    private final Map<PropertyInfo, List<PropertyNode>> assignments = new HashMap<>();
    private final Map<PropertyNode, PropertyInfo> propertyInfo = new IdentityHashMap<>();
    private final Map<PropertyNode, LinkedHashMap<PropertyNode, Dependency>> dependencies = new IdentityHashMap<>();
    private final Map<PropertyNode, LinkedHashMap<PropertyNode, Dependency>> effectiveDependencies = new IdentityHashMap<>();
    private final Map<PropertyNode, VisitState> visitStates = new IdentityHashMap<>();
    private final List<PropertyNode> visitStack = new ArrayList<>();
    private final List<PropertyNode> result = new ArrayList<>();

    public PropertyAssignmentSorter(ObjectNode objectNode, List<PropertyNode> properties) {
        this.objectNode = objectNode;
        this.objectType = TypeHelper.getTypeInstance(objectNode);
        this.properties = List.copyOf(properties);
    }

    public List<PropertyNode> sort() {
        indexAssignments();

        for (PropertyNode property : properties) {
            collectDependencies(property);
        }

        validateHardDependencies();
        createEffectiveDependencies();

        for (PropertyNode property : properties) {
            visit(property, effectiveDependencies);
        }

        return result;
    }

    private void indexAssignments() {
        for (PropertyNode property : properties) {
            if (property.isIntrinsic()) {
                continue;
            }

            PropertyInfo info = new Resolver(property.getSourceInfo()).tryResolveProperty(
                objectType, property.isAllowQualifiedName(), property.getNames());

            if (info != null) {
                propertyInfo.put(property, info);
                assignments.computeIfAbsent(info, ignored -> new ArrayList<>()).add(property);
            }
        }
    }

    private void collectDependencies(PropertyNode property) {
        if (!propertyInfo.containsKey(property)) {
            return;
        }

        List<ObjectNode> objectStack = new ArrayList<>();
        objectStack.add(objectNode);

        property.accept(new Visitor() {
            private final Deque<Node> traversalStack = new ArrayDeque<>();

            @Override
            protected Node onVisited(Node node) {
                if (node instanceof BindingNode binding) {
                    collectExpression(
                        property,
                        binding.getPath(),
                        binding.getMode().isObservable() ? DependencyKind.SOFT : DependencyKind.HARD,
                        objectStack);

                    if (binding.getConverter() != null) {
                        collectExpression(property, binding.getConverter(), DependencyKind.HARD, objectStack);
                    }

                    if (binding.getFormat() != null) {
                        collectExpression(property, binding.getFormat(), DependencyKind.HARD, objectStack);
                    }
                }

                return node;
            }

            @Override
            protected void push(Node node) {
                traversalStack.push(node);

                if (node instanceof ObjectNode nestedObject) {
                    objectStack.add(nestedObject);
                }
            }

            @Override
            protected void pop() {
                if (traversalStack.pop() instanceof ObjectNode) {
                    objectStack.remove(objectStack.size() - 1);
                }
            }
        });
    }

    private void collectExpression(
            PropertyNode target,
            Node expression,
            DependencyKind kind,
            List<ObjectNode> objectStack) {
        expression.accept(new Visitor() {
            private final Set<PathExpressionNode> invocationTargets =
                Collections.newSetFromMap(new IdentityHashMap<>());

            @Override
            protected Node onVisited(Node node) {
                if (node instanceof BindingNode || node instanceof ObjectNode) {
                    return Visitor.STOP_SUBTREE;
                }

                if (node instanceof InvocationExpressionNode invocation) {
                    addInvocationTarget(invocation.getPathTarget());
                    addInvocationTarget(invocation.getInversePath());
                } else if (node instanceof FunctionExpressionNode function) {
                    addInvocationTarget(function.getPath());
                    addInvocationTarget(function.getInversePath());
                } else if (node instanceof ConstructorExpressionNode constructor) {
                    addInvocationTarget(constructor.getInversePath());
                } else if (node instanceof PathExpressionNode path && !invocationTargets.contains(path)) {
                    addDependency(target, path, kind, objectStack);
                }

                return node;
            }

            private void addInvocationTarget(PathExpressionNode path) {
                if (path != null && path.getSegments().size() == 1) {
                    invocationTargets.add(path);
                }
            }
        });
    }

    private void addDependency(
            PropertyNode target,
            PathExpressionNode path,
            DependencyKind kind,
            List<ObjectNode> objectStack) {
        BindingContextSelector selector = path.getBindingContext().getSelector();
        if (selector == BindingContextSelector.STATIC
                || selector == BindingContextSelector.CONTEXT
                || selector == BindingContextSelector.TEMPLATED_ITEM
                || path.getSegments().isEmpty()) {
            return;
        }

        int bindingDistance = path.getBindingContext().getBindingDistance();
        if (bindingDistance < 0 || bindingDistance >= objectStack.size()) {
            return;
        }

        ObjectNode sourceObject = objectStack.get(objectStack.size() - bindingDistance - 1);
        if (sourceObject != objectNode) {
            return;
        }

        PathSegmentNode firstSegment = path.getSegments().get(0);
        if (firstSegment.isObservableSelector() && path.getSegments().size() == 1) {
            return;
        }

        PropertyInfo source = resolveSourceProperty(firstSegment);
        List<PropertyNode> sourceAssignments = source != null ? assignments.get(source) : null;
        if (sourceAssignments == null) {
            return;
        }

        LinkedHashMap<PropertyNode, Dependency> targetDependencies =
            dependencies.computeIfAbsent(target, ignored -> new LinkedHashMap<>());

        for (PropertyNode sourceAssignment : sourceAssignments) {
            Dependency dependency = new Dependency(sourceAssignment, path.getSourceInfo(), kind);
            Dependency existing = targetDependencies.get(sourceAssignment);

            if (existing == null || existing.kind() == DependencyKind.SOFT && kind == DependencyKind.HARD) {
                targetDependencies.put(sourceAssignment, dependency);
            }
        }
    }

    private PropertyInfo resolveSourceProperty(PathSegmentNode segment) {
        Resolver resolver = new Resolver(segment.getSourceInfo());

        return segment instanceof AttachedSegmentNode attached
            ? resolver.tryResolveProperty(
                objectType, false,
                attached.getDeclaringType().getName(),
                attached.getPropertyName().getName())
            : resolver.tryResolveProperty(objectType, false, segment.getText());
    }

    private void validateHardDependencies() {
        for (PropertyNode property : properties) {
            visit(property, dependencies, DependencyKind.HARD);
        }

        visitStates.clear();
        visitStack.clear();
    }

    private void createEffectiveDependencies() {
        for (PropertyNode target : properties) {
            Map<PropertyNode, Dependency> targetDependencies = dependencies.get(target);
            if (targetDependencies == null) {
                continue;
            }

            for (Dependency dependency : targetDependencies.values()) {
                if (dependency.kind() == DependencyKind.HARD) {
                    addEffectiveDependency(target, dependency);
                }
            }
        }

        for (PropertyNode target : properties) {
            Map<PropertyNode, Dependency> targetDependencies = dependencies.get(target);
            if (targetDependencies == null) {
                continue;
            }

            for (Dependency dependency : targetDependencies.values()) {
                if (dependency.kind() == DependencyKind.SOFT && !wouldCreateCycle(target, dependency.property())) {
                    addEffectiveDependency(target, dependency);
                }
            }
        }
    }

    private void addEffectiveDependency(PropertyNode target, Dependency dependency) {
        effectiveDependencies
            .computeIfAbsent(target, ignored -> new LinkedHashMap<>())
            .put(dependency.property(), dependency);
    }

    private boolean wouldCreateCycle(PropertyNode target, PropertyNode source) {
        return source == target || hasPrerequisite(
            source, target, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private boolean hasPrerequisite(
            PropertyNode property,
            PropertyNode prerequisite,
            Set<PropertyNode> visited) {
        if (!visited.add(property)) {
            return false;
        }

        Map<PropertyNode, Dependency> propertyDependencies = effectiveDependencies.get(property);
        if (propertyDependencies == null) {
            return false;
        }

        for (Dependency dependency : propertyDependencies.values()) {
            if (dependency.property() == prerequisite
                    || hasPrerequisite(dependency.property(), prerequisite, visited)) {
                return true;
            }
        }

        return false;
    }

    private void visit(
            PropertyNode property,
            Map<PropertyNode, ? extends Map<PropertyNode, Dependency>> graph,
            DependencyKind kind) {
        VisitState state = visitStates.get(property);
        if (state == VisitState.VISITED) {
            return;
        }

        visitStates.put(property, VisitState.VISITING);
        visitStack.add(property);

        Map<PropertyNode, Dependency> propertyDependencies = graph.get(property);
        if (propertyDependencies != null) {
            for (Dependency dependency : propertyDependencies.values()) {
                if (dependency.kind() != kind) {
                    continue;
                }

                if (visitStates.get(dependency.property()) == VisitState.VISITING) {
                    throw cycleError(dependency);
                }

                visit(dependency.property(), graph, kind);
            }
        }

        visitStack.remove(visitStack.size() - 1);
        visitStates.put(property, VisitState.VISITED);
    }

    private void visit(
            PropertyNode property,
            Map<PropertyNode, ? extends Map<PropertyNode, Dependency>> graph) {
        VisitState state = visitStates.get(property);
        if (state == VisitState.VISITED) {
            return;
        }

        if (state == VisitState.VISITING) {
            throw new IllegalStateException("Effective property dependency graph contains a cycle");
        }

        visitStates.put(property, VisitState.VISITING);

        Map<PropertyNode, Dependency> propertyDependencies = graph.get(property);
        if (propertyDependencies != null) {
            for (Dependency dependency : propertyDependencies.values()) {
                visit(dependency.property(), graph);
            }
        }

        visitStates.put(property, VisitState.VISITED);
        result.add(property);
    }

    private MarkupException cycleError(Dependency closingDependency) {
        int cycleStart = visitStack.indexOf(closingDependency.property());
        List<PropertyInfo> cycle = new ArrayList<>();

        for (int i = cycleStart; i < visitStack.size(); ++i) {
            cycle.add(propertyInfo.get(visitStack.get(i)));
        }

        cycle.add(propertyInfo.get(closingDependency.property()));

        return PropertyAssignmentErrors.cyclicPropertyAssignment(closingDependency.sourceInfo(), cycle);
    }
}
