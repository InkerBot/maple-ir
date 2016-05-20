package org.rsdeob.stdlib.call;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.call.CallGraph.Invocation;
import org.rsdeob.stdlib.collections.FastGraph;
import org.rsdeob.stdlib.klass.ClassTree;

public class CallGraph extends FastGraph<MethodNode, Invocation> {
	
	private final CallgraphAdapter adapter;
	private final ClassTree classTree;
	
	public CallGraph(CallgraphAdapter adapter, ClassTree classTree) {
		this.adapter = adapter;
		this.classTree = classTree;
		
		reduce();
	}
	
	public ClassTree getTree() {
		return classTree;
	}

	@Override
	protected MethodNode getSource(MethodNode n, Invocation e) {
		return e.callee;
	}

	@Override
	protected MethodNode getDestination(MethodNode n, Invocation e) {
		return e.caller;
	}
	
	private List<MethodNode> findEntries(ClassTree tree, ClassNode cn) {
		List<MethodNode> methods = new ArrayList<MethodNode>();
		for (MethodNode mn : cn.methods) {
			if (adapter.shouldMap(this, mn)) {
				methods.add(mn);
			}
		}
		return methods;
	}
	
	private void reduce() {
		int total = 0, removed = 0, prot = 0;
		int lastRemoved = 0, i = 1;

		do {
			lastRemoved = removed;
			List<MethodNode> entries = new ArrayList<>();
			for(ClassNode cn : classTree.getClasses().values()) {
				if (i == 1) {
					total += cn.methods.size();
				}
				entries.addAll(findEntries(classTree, cn));
			}
			prot += entries.size();
			for(MethodNode m : entries) {
				traverse(m);
			}
			
			for (ClassNode cn : classTree) {
				ListIterator<MethodNode> lit = cn.methods.listIterator();
				while (lit.hasNext()) {
					MethodNode mn = lit.next();
					if(!containsReverseVertex(mn)) {
						lit.remove();
						removed++;
					}
				}
			}
			
			clear();
			
			int d = removed - lastRemoved;
			if(d > 0) {
				System.out.printf("   Pass %d: removed %d methods%n", i, d);
			}
			
			i++;
		} while((removed - lastRemoved) != 0);

		System.out.printf("   %d protected methods.%n", prot);
		System.out.printf("   Found %d/%d used methods (removed %d dummy methods).%n", (total - removed), total, removed);
	}
	
	private void traverse(MethodNode m) {
		if(containsVertex(m)) {
			return;
		}
		
		addVertex(m);
		
		outer: for(AbstractInsnNode ain : m.instructions.toArray()) {
			if(ain instanceof MethodInsnNode) {
				MethodInsnNode min = (MethodInsnNode) ain;
				if (classTree.containsKey(min.owner)) {
					ClassNode cn = classTree.getClass(min.owner);
					MethodNode edge = cn.getMethod(min.name, min.desc, min.opcode() == Opcodes.INVOKESTATIC);
					if (edge != null) {
						Invocation invocation = new Invocation(m, edge);
						addEdge(m, invocation);
						traverse(edge);
						continue;
					}
					for (ClassNode superNode : classTree.getSupers(cn)) {
						MethodNode superedge = superNode.getMethod(min.name, min.desc, min.opcode() == Opcodes.INVOKESTATIC);
						if (superedge != null) {
							Invocation invocation = new Invocation(m, superedge);
							addEdge(m, invocation);
							traverse(superedge);
							continue outer;
						}
					}
				}
			}
		}
	}

	public static class Invocation {
		private final MethodNode caller;
		private final MethodNode callee;
		
		public Invocation(MethodNode caller, MethodNode callee) {
			this.caller = caller;
			this.callee = callee;
		}

		public MethodNode getCaller() {
			return caller;
		}

		public MethodNode getCallee() {
			return callee;
		}
	}
	
	public static interface CallgraphAdapter {
		boolean shouldMap(CallGraph graph, MethodNode m);
	}
}