package org.mapleir.stdlib.ir.transform.ssa;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.BackwardsFlowAnalyser;
import org.mapleir.stdlib.ir.transform.Liveness;

public class SSALivenessAnalyser extends BackwardsFlowAnalyser<BasicBlock, FlowEdge<BasicBlock>, Set<Local>> implements Liveness<BasicBlock> {

	private NullPermeableHashMap<BasicBlock, Set<Local>> def;
	private NullPermeableHashMap<BasicBlock, Set<Local>> phiDef;
	private NullPermeableHashMap<BasicBlock, Set<Local>> phiUse;
	
	
	public SSALivenessAnalyser(FlowGraph<BasicBlock, FlowEdge<BasicBlock>> graph, boolean commit) {
		super(graph, commit);
	}
	
	public SSALivenessAnalyser(FlowGraph<BasicBlock, FlowEdge<BasicBlock>> graph) {
		this(graph, true);
	}
	
	@Override
	protected void init() {
		def = new NullPermeableHashMap<>(new SetCreator<>());
		phiDef = new NullPermeableHashMap<>(new SetCreator<>());
		phiUse = new NullPermeableHashMap<>(new SetCreator<>());

		for (BasicBlock b : graph.vertices()) {
			for (Statement stmt : b.getStatements()) {
				for (Statement s : Statement.enumerate(stmt)) {
					if (s instanceof CopyVarStatement) {
						CopyVarStatement copy = (CopyVarStatement) s;
						
						Local l = copy.getVariable().getLocal();
						Expression expr = copy.getExpression();
						if(expr instanceof PhiExpression) {
							phiDef.getNonNull(b).add(l);
							Set<Local> set = phiUse.getNonNull(b);
							for(Expression e : ((PhiExpression) expr).getLocals().values()) {
								for(Statement s1 : Statement.enumerate(e)) {
									if(s1 instanceof VarExpression) {
										VarExpression v = (VarExpression) s1;
										set.add(v.getLocal());
									}
								}
							}
						} else {
							def.getNonNull(b).add(l);
						}
					}
				}
			}
		}

		super.init();
	}

	@Override
	protected Set<Local> newState() {
		return new HashSet<>();
	}

	@Override
	protected Set<Local> newEntryState() {
		return new HashSet<>();
	}

	@Override
	protected void merge(BasicBlock srcB, Set<Local> srcOut, BasicBlock dstB, Set<Local> dstIn, Set<Local> out) {
		out.addAll(srcOut);
		flowThrough(dstB, dstIn, srcB, out);
		out.addAll(srcOut);
	}
	
	@Override
	protected void flowThrough(BasicBlock dstB, Set<Local> dstIn, BasicBlock srcB, Set<Local> srcOut) {
		// propagate upwards simple flow.

		Set<Local> defs = def.getNonNull(srcB);
		Set<Local> phiDefs = phiDef.getNonNull(dstB);
		for(Local l : dstIn) {
			if(phiDefs.contains(l)) {
				srcOut.remove(l);
			} else {
				srcOut.add(l);
			}
		}
//		for(Entry<Local, Boolean> e : dstIn.entrySet()) {
//			// upwards propagation cases:
//			
//			// dst-live-in: {var}
//			//  this could be because var is the target of a phi
//			//  in which case it is considered live-in to the dst
//			//  but dead-out to the src block.
//			// or
//			//  if the var isn't the target of a phi, then it means
//			//  that the local is genuinely live-in and so we can
//			//  just propagate it across the block boundary.
//			Local l = e.getKey();
//			if(phiDefs.contains(l)) {
//				srcOut.put(l, false);
//			} else {
//				srcOut.put(l, srcOut.get(l) || e.getValue());
//			}
//		}
		
		// phi uses are considered live-out for the src and semi
		// live-in for the dst.
//		for(Local l : phiUse.getNonNull(dstB)) {
//			if(defs.contains(l)) {
//				srcOut.put(l, true);
//			}
//		}
		
		for(Local l : phiUse.getNonNull(dstB)) {
			if(defs.contains(l)) {
				srcOut.add(l);
			}
		}
	}
	
	@Override
	protected void execute(BasicBlock b, Set<Local> out, Set<Local> in) {
//		for(Entry<Local, Boolean> e : out.entrySet()) {
//			Local l = e.getKey();
//			in.put(l, e.getValue());
//		}
		in.addAll(out);
		
		Set<Local> defs = def.getNonNull(b);
		
		in.removeAll(defs);
//		for(Local l : defs) {
//			in.put(l, false);
//		}
		
		for(Statement stmt : b.getStatements()) {
			if(stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				if(copy.getExpression() instanceof PhiExpression) {
					in.add(copy.getVariable().getLocal());
					// in.put(copy.getVariable().getLocal(), true);
					continue;
				}
			}
			
			// since we are skipping phis, the phi argument variables are
			// considered dead-in unless they are used further on in the block
			// in a non phi statement. this is because the phis are on the
			// edges and not in the actual block.
			
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					VarExpression var = (VarExpression) s;
					Local l = var.getLocal();
					// if it was defined in this block, then it can't be live-in,
					//    UNLESS: it was defined by a phi, in which case it is
					//            in fact live-in.
					if(!defs.contains(l)) {
						in.add(l);
//						in.put(l, true);
					}
				}
			}
		}
	}

	@Override
	protected boolean equals(Set<Local> s1, Set<Local> s2) {
		return s1.equals(s2);
	}
	
	@Override
	protected void copy(Set<Local> src, Set<Local> dst) {
		dst.addAll(src);
	}

	@Override
	protected void flowException(BasicBlock srcB, Set<Local> src, BasicBlock dstB, Set<Local> dst) {
		throw new UnsupportedOperationException();

	}
}