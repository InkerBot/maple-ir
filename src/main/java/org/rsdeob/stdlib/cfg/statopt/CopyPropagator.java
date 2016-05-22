package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.RootStatement;

import java.util.HashMap;

public class CopyPropagator {
	private final ControlFlowGraph cfg;
	private final HashMap<BasicBlock, DataFlowState> dataFlow;
	private final RootStatement root;

	public CopyPropagator(ControlFlowGraph cfg){
		this.cfg = cfg;
		root = cfg.getRoot();
		dataFlow = (new DataFlowAnalyzer(cfg)).computeForward();
	}

	public void compute() {

	}
	// rewrite this later
}