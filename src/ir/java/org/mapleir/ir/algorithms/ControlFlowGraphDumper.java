package org.mapleir.ir.algorithms;

import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.*;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.stdlib.collections.IndexedList;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ControlFlowGraphDumper {
	private final ControlFlowGraph cfg;
	private final MethodNode m;
	
	private IndexedList<BasicBlock> order;
	private LabelNode terminalLabel;
	
	public ControlFlowGraphDumper(ControlFlowGraph cfg, MethodNode m) {
		this.cfg = cfg;
		this.m = m;
	}
	
	public void dump() {
		// Clear methodnode
		m.instructions.removeAll(true);
		m.tryCatchBlocks.clear();
		m.visitCode();
		for (BasicBlock b : cfg.vertices()) {
			b.resetLabel();
		}

		// Linearize
		linearize();
		if (!new ArrayList<>(order).equals(new ArrayList<>(cfg.vertices()))) {
			// System.err.println("[warn] Differing linearizations: " + m);
			// printOrdering(new ArrayList<>(cfg.vertices()));
			// printOrdering(order);
			// cfg.makeDotWriter().setName(m.owner.name + "#" + m.name + m.desc).export();
		}
		
		// Fix edges
		naturalise();
		
		// Dump code
		for (BasicBlock b : order) {
			m.visitLabel(b.getLabel());
			for (Stmt stmt : b) {
				stmt.toCode(m, null);
			}
		}
		terminalLabel = new LabelNode();
		m.visitLabel(terminalLabel.getLabel());
		
		// Verify
		verify();

		// Dump ranges
		for (ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			dumpRange(er);
		}
		
		m.visitEnd();
	}
	
	private void verify() {
		ListIterator<BasicBlock> it = order.listIterator();
		while(it.hasNext()) {
			BasicBlock b = it.next();
			
			for(FlowEdge<BasicBlock> e: cfg.getEdges(b)) {
				if(e.getType() == FlowEdges.IMMEDIATE) {
					if(it.hasNext()) {
						BasicBlock n = it.next();
						it.previous();
						
						if(n != e.dst) {
							throw new IllegalStateException("Illegal flow " + e + " > " + n);
						}
					} else {
						throw new IllegalStateException("Trailing " + e);
					}
				}
			}
		}
	}
	
	private void printOrdering(List<BasicBlock> order) {
		for (int i = 0; i < order.size(); i++) {
			BasicBlock b = order.get(i);
			System.err.print(b.getId());
			BasicBlock next = b.getImmediate();
			if (next != null) {
				if (next == order.get(i + 1)) {
					System.err.print("->");
				} else {
					throw new IllegalStateException("WTF");
				}
			} else {
				System.err.print(" ");
			}
		}
		System.err.println();
	}
	
	private void dumpRange(ExceptionRange<BasicBlock> er) {
		// Determine exception type
		Type type;
		Set<Type> typeSet = er.getTypes();
		if (typeSet.size() != 1) {
			// TODO: fix base exception
			type = ExceptionAnalysis.THROWABLE;
		} else {
			type = typeSet.iterator().next();
		}
		
		final Label handler = er.getHandler().getLabel();
		List<BasicBlock> range = er.get();
		range.sort(Comparator.comparing(order::indexOf));
		
		Label start = range.get(0).getLabel();
		int rangeIdx = 0, orderIdx = order.indexOf(range.get(rangeIdx));
		for (;;) {
			// check for endpoints
			if (orderIdx + 1 == order.size()) { // end of method
				m.visitTryCatchBlock(start, terminalLabel.getLabel(), handler, type.getInternalName());
				break;
			} else if (rangeIdx + 1 == range.size()) { // end of range
				Label end = order.get(orderIdx + 1).getLabel();
				m.visitTryCatchBlock(start, end, handler, type.getInternalName());
				break;
			}
			
			// check for discontinuity
			BasicBlock nextBlock = range.get(rangeIdx + 1);
			int nextOrderIdx = order.indexOf(nextBlock);
			if (nextOrderIdx - orderIdx > 1) { // blocks in-between, end the handler and begin anew
				System.err.println("[warn] Had to split up a range: " + m);
				Label end = order.get(orderIdx + 1).getLabel();
				m.visitTryCatchBlock(start, end, handler, type.getInternalName());
				start = nextBlock.getLabel();
			}

			// next
			rangeIdx++;
			if (nextOrderIdx != -1)
				orderIdx = nextOrderIdx;
		}
	}
	
	// Recursively apply Tarjan's SCC algorithm
	private static List<BlockBundle> linearize(Collection<BlockBundle> bundles, BundleGraph fullGraph, BlockBundle entryBundle) {
		BundleGraph subgraph = fullGraph.inducedSubgraph(bundles);
		
		TarjanSCC<BlockBundle> sccComputor = new TarjanSCC<>(subgraph);
		sccComputor.search(entryBundle);
		for(BlockBundle b : bundles) {
			if(sccComputor.low(b) == -1) {
				sccComputor.search(b);
			}
		}
		
		List<BlockBundle> order = new ArrayList<>();
		
		// Flatten
		List<List<BlockBundle>> components = sccComputor.getComponents();
		if (components.size() == 1)
			order.addAll(components.get(0));
		else for (List<BlockBundle> scc : components)
			order.addAll(linearize(scc, subgraph, chooseEntry(subgraph, scc)));
		return order;
	}
	
	private static BlockBundle chooseEntry(BundleGraph graph, List<BlockBundle> scc) {
		Set<BlockBundle> sccSet = new HashSet<>(scc);
		Set<BlockBundle> candidates = new HashSet<>(scc);
		candidates.removeIf(bundle -> { // No incoming edges from within the SCC.
			for (FastGraphEdge<BlockBundle> e : graph.getReverseEdges(bundle)) {
				if (sccSet.contains(e.src))
					return true;
			}
			return false;
		});
		if (candidates.isEmpty())
			return scc.get(0);
		return candidates.iterator().next();
	}
	
	private void linearize() {
		if (cfg.getEntries().size() != 1)
			throw new IllegalStateException("CFG doesn't have exactly 1 entry");
		BasicBlock entry = cfg.getEntries().iterator().next();
		
		// Build bundle graph
		Map<BasicBlock, BlockBundle> bundles = new HashMap<>();
		Map<BlockBundle, List<BlockBundle>> bunches = new HashMap<>();
		
		// Build bundles
		List<BasicBlock> postorder = new SimpleDfs<>(cfg, entry, SimpleDfs.POST).getPostOrder();
		for (int i = postorder.size() - 1; i >= 0; i--) {
			BasicBlock b = postorder.get(i);
			if (bundles.containsKey(b)) // Already in a bundle
				continue;
			
			if (b.getIncomingImmediateEdge() != null) // Look for heads of bundles only
				continue;
			
			BlockBundle bundle = new BlockBundle();
			while (b != null) {
				bundle.add(b);
				bundles.put(b, bundle);
				b = b.getImmediate();
			}
			
			List<BlockBundle> bunch = new ArrayList<>();
			bunch.add(bundle);
			bunches.put(bundle, bunch);
		}
		
		// Group bundles by exception ranges
		for (ExceptionRange<BasicBlock> range : cfg.getRanges()) {
			BlockBundle prevBundle = null;
			for (BasicBlock b : range.getNodes()) {
				BlockBundle curBundle = bundles.get(b);
				if (prevBundle == null) {
					prevBundle = curBundle;
					continue;
				}
				if (curBundle != prevBundle) {
					List<BlockBundle> bunchA = bunches.get(prevBundle);
					List<BlockBundle> bunchB = bunches.get(curBundle);
					if (bunchA != bunchB) {
						bunchA.addAll(bunchB);
						for (BlockBundle bundle : bunchB) {
							bunches.put(bundle, bunchA);
						}
					}
					prevBundle = curBundle;
				}
			}
		}
		
		// Rebuild bundles
		bundles.clear();
		for (Map.Entry<BlockBundle, List<BlockBundle>> e : bunches.entrySet()) {
			BlockBundle bundle = e.getKey();
			if (bundles.containsKey(bundle.getFirst()))
				continue;
			BlockBundle bunch = new BlockBundle();
			e.getValue().forEach(bunch::addAll);
			for (BasicBlock b : bunch)
				bundles.put(b, bunch);
		}
		
		BundleGraph bundleGraph = new BundleGraph();
		BlockBundle entryBundle = bundles.get(entry);
		bundleGraph.addVertex(entryBundle);
		for (BasicBlock b : postorder) {
			for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				if (e instanceof ImmediateEdge)
					continue;
				BlockBundle src = bundles.get(b);
				bundleGraph.addEdge(src, new FastGraphEdge<>(src, bundles.get(e.dst)));
			}
		}
		
		// Flatten
		order = new IndexedList<>();
		Set<BlockBundle> bundlesSet = new HashSet<>(bundles.values()); // for efficiency
		ControlFlowGraphDumper.linearize(bundlesSet, bundleGraph, entryBundle).forEach(order::addAll);
	}
	
	private void naturalise() {
		for (int i = 0; i < order.size(); i++) {
			BasicBlock b = order.get(i);
			for (FlowEdge<BasicBlock> e : new HashSet<>(cfg.getEdges(b))) {
				BasicBlock dst = e.dst;
				if (e instanceof ImmediateEdge && order.indexOf(dst) != i + 1) { // Fix immediates
					b.add(new UnconditionalJumpStmt(dst));
					cfg.removeEdge(b, e);
					cfg.addEdge(b, new UnconditionalJumpEdge<>(b, dst));
					
					System.err.println("[warn] Had to fixup immediate to goto: " + cfg.getMethod());
				} else if (e instanceof UnconditionalJumpEdge && order.indexOf(dst) == i + 1) { // Remove extraneous gotos
					for (ListIterator<Stmt> it = b.listIterator(b.size()); it.hasPrevious(); ) {
						if (it.previous() instanceof UnconditionalJumpStmt) {
							it.remove();
							break;
						}
					}
					cfg.removeEdge(b, e);
					cfg.addEdge(b, new ImmediateEdge<>(b, dst));
				}
			}
		}
	}
	
	// TODO: default graph impl
	private static class BundleGraph extends FastDirectedGraph<BlockBundle, FastGraphEdge<BlockBundle>> {
		@Override
		public boolean excavate(BlockBundle basicBlocks) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean jam(BlockBundle pred, BlockBundle succ, BlockBundle basicBlocks) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraphEdge<BlockBundle> clone(FastGraphEdge<BlockBundle> edge, BlockBundle oldN, BlockBundle newN) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraphEdge<BlockBundle> invert(FastGraphEdge<BlockBundle> edge) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraph<BlockBundle, FastGraphEdge<BlockBundle>> copy() {
			throw new UnsupportedOperationException();
		}
		
		// todo: move up to FastGraph!
		public BundleGraph inducedSubgraph(Collection<BlockBundle> vertices) {
			BundleGraph subgraph = new BundleGraph();
			for (BlockBundle n : vertices) {
				subgraph.addVertex(n);
				for (FastGraphEdge<BlockBundle> e : getEdges(n)) {
					if (vertices.contains(e.dst))
						subgraph.addEdge(n, e);
				}
			}
			return subgraph;
		}
	}
	
	private static class BlockBundle extends ArrayList<BasicBlock> implements FastGraphVertex {
		private BasicBlock first = null;
		
		private BasicBlock getFirst() {
			if (first == null)
				first = get(0);
			return first;
		}
		
		@Override
		public String getId() {
			return getFirst().getId();
		}
		
		@Override
		public int getNumericId() {
			return getFirst().getNumericId();
		}
		
		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			for (Iterator<BasicBlock> it = this.iterator(); it.hasNext(); ) {
				BasicBlock b = it.next();
				s.append(b.getId());
				if (it.hasNext())
					s.append("->");
			}
			return s.toString();
		}
		
		@Override
		public int hashCode() {
			return getFirst().hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof BlockBundle))
				return false;
			return ((BlockBundle) o).getFirst().equals(getFirst());
		}
	}
}
