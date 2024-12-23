package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.*;

public class PrintMachineCodeVisitor implements ParserVisitor {
    private PrintWriter m_writer = null;

    private int MAX_REGISTERS_COUNT = 256;

    private final ArrayList<String> RETURNS = new ArrayList<>();
    private final ArrayList<MachineCodeLine> CODE = new ArrayList<>();

    private final ArrayList<String> MODIFIED = new ArrayList<>();
    private final ArrayList<String> REGISTERS = new ArrayList<>();

    private final HashMap<String, String> OPERATIONS = new HashMap<>();

    public PrintMachineCodeVisitor(PrintWriter writer) {
        m_writer = writer;

        OPERATIONS.put("+", "ADD");
        OPERATIONS.put("-", "MIN");
        OPERATIONS.put("*", "MUL");
        OPERATIONS.put("/", "DIV");
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, null);

        computeLifeVar();
        computeNextUse();

        printMachineCode();

        return null;
    }

    @Override
    public Object visit(ASTNumberRegister node, Object data) {
        MAX_REGISTERS_COUNT = ((ASTIntValue) node.jjtGetChild(0)).getValue();
        return null;
    }

    @Override
    public Object visit(ASTReturnStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            RETURNS.add(((ASTIdentifier) node.jjtGetChild(i)).getValue());
        }
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String operation = (String) node.getOp();
        String right = (String) node.jjtGetChild(2).jjtAccept(this, null);

        CODE.add(new MachineCodeLine(operation, assign, left, right));

        return null;
    }

    @Override
    public Object visit(ASTAssignUnaryStmt node, Object data) {
        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = "#0";
        String operation = "-";
        String right = (String) node.jjtGetChild(1).jjtAccept(this, null);

        CODE.add(new MachineCodeLine(operation, assign, left, right));

        return null;
    }

    @Override
    public Object visit(ASTAssignDirectStmt node, Object data) {
        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = "#0";
        String operation = "+";
        String right = (String) node.jjtGetChild(1).jjtAccept(this, null);

        CODE.add(new MachineCodeLine(operation, assign, left, right));

        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, null);
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return "#" + node.getValue();
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        return node.getValue();
    }

    private void computeLifeVar() {
        Integer lastNode = CODE.size() - 1;
        CODE.get(lastNode).Life_OUT.addAll(RETURNS);

        for (int i = lastNode; i >= 0; i--) {
            MachineCodeLine line = CODE.get(i);

            if (i < lastNode) {
                line.Life_OUT.addAll(CODE.get(i + 1).Life_IN);
            }

            line.Life_IN.addAll(line.Life_OUT);
            line.Life_IN.removeAll(line.DEF);
            line.Life_IN.addAll(line.REF);
        }
    }

    private void computeNextUse() {
        for (int i = CODE.size() - 1; i >= 0; i--) {
            MachineCodeLine line = CODE.get(i);

            if (i < CODE.size() - 1) {
                line.Next_OUT = (NextUse) CODE.get(i + 1).Next_IN.clone();
            }

            for (String var : line.Next_OUT.nextUse.keySet()) {
                if (line.DEF.contains(var)) {
                    continue;
                }

                for (Integer next : line.Next_OUT.get(var)) {
                    line.Next_IN.add(var, next);
                }
            }

            for (String var : line.REF) {
                line.Next_IN.add(var, i);
            }
        }
    }

    /**
     * This function should generate the LD and ST when needed.
     */
    public String chooseRegister(String variable, HashSet<String> life, NextUse next, boolean loadIfNotFound) {
        if (variable.charAt(0) == '#') {
            return variable;
        }

        if (REGISTERS.contains(variable)) {
            return "R" + REGISTERS.indexOf(variable);
        }

        if (REGISTERS.size() < MAX_REGISTERS_COUNT) {
            REGISTERS.add(variable);
            String register = "R" + (REGISTERS.size() - 1);

            if (loadIfNotFound) {
                String code = String.format("LD %s, %s", register, variable);
                m_writer.println(code);
            }

            return register;
        }

        int maxIndex = 0;
        String maxVar = "";
        int maxNext = -1;

        for (int i = 0; i < REGISTERS.size(); i++) {
            String var = REGISTERS.get(i);
            if (!next.nextUse.containsKey(var)) {
                maxVar = var;
                maxIndex = i;
                break;
            }

            if (next.get(var).get(0) > maxNext) {
                maxNext = next.get(var).get(0);
                maxVar = var;
                maxIndex = i;
            }
        }

        REGISTERS.set(maxIndex, variable);
        String register = "R" + maxIndex;

        if (MODIFIED.contains(maxVar) && life.contains(maxVar)) {
            String code = String.format("ST %s, %s", maxVar, register);
            m_writer.println(code);
        }

        if (loadIfNotFound) {
            String code2 = String.format("LD %s, %s", register, variable);
            m_writer.println(code2);
        }

        return register;
    }

    /**
     * Print the machine code in the output file
     */
    public void printMachineCode() {
        for (int i = 0; i < CODE.size(); i++) {
            m_writer.println("// Step " + i);
            MachineCodeLine line = CODE.get(i);

            String left = chooseRegister(line.LEFT, line.Life_IN, line.Next_IN, true);
            String right = chooseRegister(line.RIGHT, line.Life_IN, line.Next_IN, true);
            String assign = chooseRegister(line.ASSIGN, line.Life_OUT, line.Next_OUT, false);
            String operation = line.OPERATION;

            Boolean reassigned = assign.equals(left) || assign.equals(right);
            Boolean operand_zero = left.equals("#0") || right.equals("#0");
            Boolean addition_substraction = operation.equals("ADD") || operation.equals("SUB");
            Boolean useless_assignment = reassigned && operand_zero && addition_substraction;

            if (!useless_assignment) {
                String code = String.format("%s %s, %s, %s", operation, assign, left, right);
                m_writer.println(code);
            }

            MODIFIED.add(line.ASSIGN);
            m_writer.println(CODE.get(i));
        }

        for (String var : REGISTERS) {
            if (RETURNS.contains(var) && MODIFIED.contains(var)) {
                String register = "R" + REGISTERS.indexOf(var);
                String code = String.format("ST %s, %s", var, register);
                m_writer.println(code);
            }
        }
    }

    /**
     * Order a set in alphabetic order
     *
     * @param set The set to order
     * @return The ordered list
     */
    public List<String> orderedSet(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    /**
     * A class to store and manage next uses.
     */
    private class NextUse {
        public HashMap<String, ArrayList<Integer>> nextUse = new HashMap<>();

        public NextUse() {}

        public NextUse(HashMap<String, ArrayList<Integer>> nextUse) {
            this.nextUse = nextUse;
        }

        public ArrayList<Integer> get(String s) {
            return nextUse.get(s);
        }

        public void add(String s, int i) {
            if (!nextUse.containsKey(s)) {
                nextUse.put(s, new ArrayList<>());
            }
            nextUse.get(s).add(i);
        }

        public String toString() {
            ArrayList<String> items = new ArrayList<>();
            for (String key : orderedSet(nextUse.keySet())) {
                Collections.sort(nextUse.get(key));
                items.add(String.format("%s:%s", key, nextUse.get(key)));
            }
            return String.join(", ", items);
        }

        @Override
        public Object clone() {
            return new NextUse((HashMap<String, ArrayList<Integer>>) nextUse.clone());
        }
    }

    /**
     * A struct to store the data of a machine code line.
     */
    private class MachineCodeLine {
        String OPERATION;
        String ASSIGN;
        String LEFT;
        String RIGHT;

        public HashSet<String> REF = new HashSet<>();
        public HashSet<String> DEF = new HashSet<>();

        public HashSet<String> Life_IN = new HashSet<>();
        public HashSet<String> Life_OUT = new HashSet<>();

        public NextUse Next_IN = new NextUse();
        public NextUse Next_OUT = new NextUse();

        public MachineCodeLine(String operation, String assign, String left, String right) {
            this.OPERATION = OPERATIONS.get(operation);
            this.ASSIGN = assign;
            this.LEFT = left;
            this.RIGHT = right;

            DEF.add(this.ASSIGN);
            if (this.LEFT.charAt(0) != '#')
                REF.add(this.LEFT);
            if (this.RIGHT.charAt(0) != '#')
                REF.add(this.RIGHT);
        }

        @Override
        public String toString() {
            String buffer = "";
            buffer += String.format("// Life_IN  : %s\n", Life_IN);
            buffer += String.format("// Life_OUT : %s\n", Life_OUT);
            buffer += String.format("// Next_IN  : %s\n", Next_IN);
            buffer += String.format("// Next_OUT : %s\n", Next_OUT);
            return buffer;
        }
    }
}
