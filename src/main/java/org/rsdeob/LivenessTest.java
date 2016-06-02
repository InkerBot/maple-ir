package org.rsdeob;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementBuilder;
import org.rsdeob.stdlib.cfg.ir.StatementGenerator;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.impl.DeadAssignmentEliminator;
import org.rsdeob.stdlib.cfg.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.impl.LivenessAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.impl.ValuePropagator;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class LivenessTest {

	public static void main(String[] args) throws Exception {
		ClassNode cn = new ClassNode();
		ClassReader cr = new ClassReader(LivenessTest.class.getCanonicalName());
		cr.accept(cn, 0);
		
		for(MethodNode m : cn.methods) {
			if(m.name.startsWith("test1")) {
				ControlFlowGraphBuilder cfgbuilder = new ControlFlowGraphBuilder(m);
				ControlFlowGraph cfg = cfgbuilder.build();
				
				ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
				List<BasicBlock> blocks = deobber.deobfuscate(cfg);
				GraphUtils.naturaliseGraph(cfg, blocks);
				
				StatementGenerator generator = new StatementGenerator(cfg);
				generator.init(m.maxLocals);
				generator.createExpressions();
				RootStatement root = generator.buildRoot();
				
				StatementGraph sgraph = StatementGraphBuilder.create(cfg);
				System.out.println("Processing " + m);
				System.out.println(cfg);
				System.out.println(root);
				System.out.println();

				simplify(root, sgraph, m);
//				System.out.println(root);
			}
		}
	}
	
	public static void simplify(RootStatement root, StatementGraph graph, MethodNode m) {
		while(true) {
			int change = 0;
//			System.out.println("graph1: ");
//			System.out.println(graph);
			
			DefinitionAnalyser defAnalyser = new DefinitionAnalyser(graph, m);
			defAnalyser.run();
			// change += ValuePropagator.propagateDefinitions1(root, graph, defAnalyser);

			ValuePropagator prop = new ValuePropagator(root, graph);
			prop.process(defAnalyser);
			
//			System.out.println();
//			System.out.println();
//			System.out.println("After propagation");
//			System.out.println(root);
//			System.out.println();
//			System.out.println();
			
//			System.out.println("graph2: ");
//			System.out.println(graph);
			
			LivenessAnalyser la = new LivenessAnalyser(graph);
			la.run();
			change += DeadAssignmentEliminator.run(root, graph, la);
			
			
			System.out.println();
			System.out.println();
//			System.out.println("After elimination");
			System.out.println(root);
//			System.out.println();
//			System.out.println();
//			
			
			if(change <= 0) {
				break;
			}
			
			
		}
	}
	
	void test1() {
		int x = 0;
		int z = 10;
		while(x <= 10) {
			x++;
			z = x;
		}
		System.out.println(x);
		System.out.println(x);
	}
	
	public static void main1(String[] args) throws Exception {		
		VarExpression x = new VarExpression(0, Type.INT_TYPE) {
			@Override
			public void toString(TabbedStringWriter printer) {
				printer.print('x');
			}
		};
		
		// x := 0
		// while(x != 10) {
		//    x = x + 1;
		// }

		StatementBuilder b = new StatementBuilder();
		b.add(b.assign(x, b.constant(0)));
		
		List<Statement> body = new ArrayList<>();
		body.add(b.assign(x, b.arithmetic(x, b.constant(1), Operator.ADD)));
		
		List<Statement> loop = b.whileloop(x, b.constant(10), ComparisonType.NE, body);
		for(Statement stmt : loop) {
			b.add(stmt);
		}
		
		b.add(b.call(Opcodes.INVOKESTATIC, "test", "use", "(I)V", new Expression[]{x}));
	}
}