package org.rsdeob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.BootBibl.C2;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGenerator;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.transform.impl.TrackerImpl;

public class DataFlowAnalyserBoot {

	public static void main(String[] args) throws Exception {
		ClassNode cn = new ClassNode();
		ClassReader cr = new ClassReader(DataFlowAnalyserBoot.class.getCanonicalName());
		cr.accept(cn, 0);
		
		for(MethodNode m : cn.methods) {
			if(m.name.startsWith("test3")) {
				ControlFlowGraph cfg = new ControlFlowGraphBuilder(m).build();
				StatementGenerator generator = new StatementGenerator(cfg);
				generator.init(m.maxLocals);
				generator.createExpressions();
				RootStatement root = generator.buildRoot();
				StatementGraph sgraph = StatementGraphBuilder.create(cfg);
				System.out.println("Processing " + m);
				System.out.println(cfg);
				System.out.println(root);
				System.out.println();
				
				TrackerImpl ffa = new TrackerImpl(sgraph, m);
				ffa.run();
//				ReachingAnalyser ra = new ReachingAnalyser(sgraph);
//				ra.run();
				
//				for(Statement stmt : sgraph.vertices()) {
//					System.out.println(ra.toString(stmt));
//				}
//				ffa.propagate();
				
//				System.out.println(root);
				
				System.out.println();
			}
		}
	}
	
	public void test9(Object o) {
		synchronized (o == null ? this : o) {
			System.out.println(o);
			System.out.println(this);
			test8(5, 7, 0);
		}
	}
	
	public void test8(int i, int j, int k) {
		test7(5);
		if(i > 0) {
			test8(i - 1, j, k);
		}
		test7(16);
	}
	
	public void test7(int i) {
		System.out.println(i);
		System.out.println(i + " hi " + i);
	}

	public void test4() {
		BootBibl newBoot = new BootBibl();
		newBoot.DVAL += new C2().FVAL;
		new C2().FVAL = (float) newBoot.DVAL;
		
		System.out.println(newBoot);
	}
	
	public void test6() {
		for(int i=0; i < 3; i++) {
			test5();
		}
	}
	
	public void test5() {
		BootBibl boot = new BootBibl();
		boot.DVAL = 5;
	}
	
	double DVAL = 5D;
	float FVAL = 10F;
	String SVAL = "";
	
	public void test3() {
		double d = DVAL * 847545D;
		float f = FVAL * 8573845743F;
		double c = 0;
		if(d > f) {
			c = (d + f);
		} else {
			c = (d - f);
		}
		
		System.out.println(c);
	}
	
	public void test2(int i, int j, int k) {
		int s = 283472 * i;
		int d = j * 4334857;
		int f = 345345 * (34784 * k);
		
		System.out.println(s);
		System.out.println(d);
		System.out.println(f);
	}
	
	public void test(int i) {
		System.out.println("k " + i);
	}
	
	public float ternTest2(int i) {
		return testNormalReturn(i == 8 ? 5 : 10);
	}
	
	public float testNormalReturn(int i) {
		if(i == 1) {
			return 5F;
		} else {
			return 10F;
		}
	}
	
	public float ternTest(int i) {
		return i == 1 ? testNormalReturn(i) : testNormalReturn(i);
	}
}