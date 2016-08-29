package org.mapleir.stdlib.collections.graph.flow;

import java.util.*;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public abstract class FlowGraph<N extends FastGraphVertex, E extends FlowEdge<N>> extends FastDirectedGraph<N, E> {

	protected final List<ExceptionRange<N>> ranges;
	protected final Set<N> entries;
	protected final Map<String, N> vertexIds;
	
	public FlowGraph() {
		ranges = new ArrayList<>();
		entries = new HashSet<>();
		vertexIds = new HashMap<>();
	}
	
	public FlowGraph(FlowGraph<N, E> g) {
		super(g);
		
		ranges = new ArrayList<>(g.ranges);
		entries = new HashSet<>(g.entries);
		vertexIds = new HashMap<>(g.vertexIds);
	}
	
	public N getBlock(String id) {
		return vertexIds.get(id);
	}
	
	public Set<N> getEntries() {
		return entries;
	}
	
	public void addRange(ExceptionRange<N> range) {
		if(!ranges.contains(range)) {
			ranges.add(range);
		}
	}
	
	public void removeRange(ExceptionRange<N> range) {
		ranges.remove(range);
	}
	
	public List<ExceptionRange<N>> getRanges() {
		return new ArrayList<>(ranges);
	}
	
	@Override
	public void clear() {
		super.clear();
		vertexIds.clear();
	}
	
	@Override
	public void addVertex(N v) {
		vertexIds.put(v.getId(), v);
		super.addVertex(v);
	}	
	
	@Override
	public void addEdge(N v, E e) {
		vertexIds.put(v.getId(), v);
		super.addEdge(v, e);
	}
	
	@Override
	public void replace(N old, N n) {
		if(entries.contains(old)) {
			entries.add(n);
		}
		super.replace(old, n);
	}
	
	@Override
	public void removeVertex(N v) {
		ListIterator<ExceptionRange<N>> it = ranges.listIterator();
		while(it.hasNext()) {
			ExceptionRange<N> r = it.next();
			r.removeVertex(v);
			if(r.get().isEmpty()) {
				it.remove();
			}
		}
		
		entries.remove(v);
		vertexIds.remove(v.getId());
		super.removeVertex(v);
	}
	
	public Set<N> wanderAllTrails(N from, N to, boolean forward) {
		return wanderAllTrails(from, to, forward, true);
	}

	public Set<N> wanderAllTrails(N from, N to) {
		return wanderAllTrails(from, to, true, false);
	}

	public Set<N> wanderAllTrails(N from, N to, boolean forward, boolean followExceptions) {
		Set<N> visited = new HashSet<>();
		LinkedList<N> stack = new LinkedList<>();
		stack.add(from);
		
		while(!stack.isEmpty()) {
			N s = stack.pop();
			
			Set<E> edges = forward ? getEdges(s) : getReverseEdges(s);
			for(FlowEdge<N> e : edges) {
				if(e instanceof TryCatchEdge && !followExceptions)
					continue;
				N next = forward ? e.dst : e.src;
				if(next != to && !visited.contains(next)) {
					stack.add(next);
					visited.add(next);
				}
			}
		}
		
		visited.add(from);
		
		return visited;
	}
}