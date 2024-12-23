package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Vector;

public class IntermediateCodeGenVisitor implements ParserVisitor {
    private final PrintWriter m_writer;

    public HashMap<String, VarType> SymbolTable = new HashMap<>();
    public HashMap<String, Integer> EnumValueTable = new HashMap<>();

    private int id = 0;
    private int label = 0;

    public IntermediateCodeGenVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    private String newID() {
        return "_t" + id++;
    }

    private String newLabel() {
        return "_L" + label++;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        String endProgram = newLabel();
        node.childrenAccept(this, endProgram);
        m_writer.println(endProgram);
        return null;
    }

    @Override
    public Object visit(ASTDeclaration node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        VarType varType;

        if (node.getValue() == null) {
            varName = ((ASTIdentifier) node.jjtGetChild(1)).getValue();
            varType = VarType.EnumVar;
        } else
            varType = node.getValue().equals("num") ? VarType.Number : VarType.Bool;

        SymbolTable.put(varName, varType);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        int numChildren = node.jjtGetNumChildren();

        if (numChildren == 0) {
            return null;
        }

        for (int i = 0; i < numChildren - 1; i++) {
            String endStmt = newLabel();
            node.jjtGetChild(i).jjtAccept(this, endStmt);
            m_writer.println(endStmt);
        }

        node.jjtGetChild(numChildren - 1).jjtAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTEnumStmt node, Object data) {
        String enumType = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        SymbolTable.put(enumType, VarType.EnumType);

        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            String enumValue = ((ASTIdentifier) node.jjtGetChild(i)).getValue();
            EnumValueTable.put(enumValue, i - 1);
        }

        return null;
    }

    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        String identifier = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        String currentCase = null;
        String nextCase = (String) data;
        String fallthrough = null;
        String endSwitch = (String) data;
        String[] params = new String[]{currentCase, nextCase, fallthrough, endSwitch, identifier};

        if (node.jjtGetNumChildren() == 2) {
            node.jjtGetChild(1).jjtAccept(this, params);
            return null;
        }

        params[1] = newLabel();

        for (int i = 1; i < node.jjtGetNumChildren() - 1; i++) {
            params = (String[]) node.jjtGetChild(i).jjtAccept(this, params);
            if (params[2] != null) {
                m_writer.println("goto " + params[2]);
            }
            m_writer.println(params[1]);
            params[0] = params[1];
            params[1] = newLabel();
        }

        params[1] = params[3];

        node.jjtGetChild(node.jjtGetNumChildren() - 1).jjtAccept(this, params);

        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        String[] params = (String[]) data;
        String currentCase = params[0];
        String nextCase = params[1];
        String fallthrough = params[2];
        String endSwitch = params[3];

        String switchVar = params[4];
        String caseValue = (String) node.jjtGetChild(0).jjtAccept(this, data);
        if (EnumValueTable.containsKey(caseValue)) {
            caseValue = EnumValueTable.get(caseValue).toString();
        }

        m_writer.println("if " + switchVar + " != " + caseValue + " goto " + nextCase);

        if (fallthrough != null) {
            m_writer.println(fallthrough);
        }

        node.jjtGetChild(1).jjtAccept(this, data);

        if (node.jjtGetNumChildren() == 3) {
            node.jjtGetChild(2).jjtAccept(this, endSwitch);
            fallthrough = null;
        } else {
            fallthrough = newLabel();
        }

        return new String[]{currentCase, nextCase, fallthrough, endSwitch, switchVar};
    }

    @Override
    public Object visit(ASTBreakStmt node, Object data) {
        m_writer.println("goto " + data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIfStmt node, Object data) {
        if (node.jjtGetNumChildren() == 2) {
            String ifBlock = newLabel();
            String endIf = (String) data;
            node.jjtGetChild(0).jjtAccept(this, new BoolLabel(ifBlock, endIf));
            m_writer.println(ifBlock);
            node.jjtGetChild(1).jjtAccept(this, endIf);
        } else if (node.jjtGetNumChildren() == 3) {
            String ifBlock = newLabel();
            String elseBlock = newLabel();
            String endIf = (String) data;
            node.jjtGetChild(0).jjtAccept(this, new BoolLabel(ifBlock, elseBlock));
            m_writer.println(ifBlock);
            node.jjtGetChild(1).jjtAccept(this, endIf);
            m_writer.println("goto " + endIf);
            m_writer.println(elseBlock);
            node.jjtGetChild(2).jjtAccept(this, endIf);
        }

        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String begin = newLabel();
        String whileBlock = newLabel();
        String end = (String) data;

        m_writer.println(begin);
        node.jjtGetChild(0).jjtAccept(this, new BoolLabel(whileBlock, end));
        m_writer.println(whileBlock);
        node.jjtGetChild(1).jjtAccept(this, begin);
        m_writer.println("goto " + begin);

        return null;
    }

    @Override
    public Object visit(ASTForStmt node, Object data) {
        String forCond = newLabel();
        String forIter = newLabel();
        String forBlock = newLabel();
        String endFor = (String) data;

        node.jjtGetChild(0).jjtAccept(this, null);
        m_writer.println(forCond);
        node.jjtGetChild(1).jjtAccept(this, new BoolLabel(forBlock, endFor));
        m_writer.println(forBlock);
        node.jjtGetChild(3).jjtAccept(this, forIter);
        m_writer.println(forIter);
        node.jjtGetChild(2).jjtAccept(this, null);
        m_writer.println("goto " + forCond);

        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String id = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if (SymbolTable.get(id) == VarType.Bool) {
            BoolLabel boolLabel = new BoolLabel(newLabel(), newLabel());
            node.jjtGetChild(1).jjtAccept(this, boolLabel);
            m_writer.println(boolLabel.lTrue);
            m_writer.println(id + " = 1");
            m_writer.println("goto " + data);
            m_writer.println(boolLabel.lFalse);
            m_writer.println(id + " = 0");
            return null;
        } else if (SymbolTable.get(id) == VarType.EnumVar) {
            String enumValue = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(id + " = " + EnumValueTable.get(enumValue));
            return null;
        }

        String expr = (String) node.jjtGetChild(1).jjtAccept(this, data);
        m_writer.println(id + " = " + expr);
        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    public Object codeExtAddMul(SimpleNode node, Object data, Vector<String> ops) {
        if (ops.size() == 0) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        String id = newID();
        String left = (String) node.jjtGetChild(0).jjtAccept(this, data);
        String op = ops.get(0);
        String right = (String) node.jjtGetChild(1).jjtAccept(this, data);
        m_writer.println(id + " = " + left + " " + op + " " + right);
        return id;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        String expr = (String) node.jjtGetChild(0).jjtAccept(this, data);
        int numOps = node.getOps().size();

        for (int i = 0; i < numOps; i++) {
            String id = newID();
            m_writer.println(id + " = - " + expr);
            expr = id;
        }
        return expr;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        BoolLabel boolLabel0 = (BoolLabel) data;
        BoolLabel boolLabel1 = null;
        BoolLabel boolLabel2 = new BoolLabel(boolLabel0.lTrue, boolLabel0.lFalse);
        String newLabel = newLabel();
        String op = (String) node.getOps().get(0);

        if (op.equals("&&")) {
            boolLabel1 = new BoolLabel(newLabel, boolLabel0.lFalse);
        } else if (op.equals("||")) {
            boolLabel1 = new BoolLabel(boolLabel0.lTrue, newLabel);
        }

        node.jjtGetChild(0).jjtAccept(this, boolLabel1);
        m_writer.println(newLabel);
        node.jjtGetChild(1).jjtAccept(this, boolLabel2);

        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        String left = (String) node.jjtGetChild(0).jjtAccept(this, data);
        if (node.jjtGetNumChildren() == 1) {
            return left;
        }

        String op = node.getValue();
        String right = (String) node.jjtGetChild(1).jjtAccept(this, data);
        m_writer.println("if " + left + " " + op + " " + right + " goto " + ((BoolLabel) data).lTrue);
        m_writer.println("goto " + ((BoolLabel) data).lFalse);
        return null;
    }

    @Override
    public Object visit(ASTNotExpr node, Object data) {
        if (node.getOps().size() % 2 == 0) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        return node.jjtGetChild(0).jjtAccept(this, new BoolLabel(((BoolLabel) data).lFalse, ((BoolLabel) data).lTrue));
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        if (node.getValue()) {
            m_writer.println("goto " + ((BoolLabel) data).lTrue);
        } else {
            m_writer.println("goto " + ((BoolLabel) data).lFalse);
        }
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        String id = node.getValue();
        VarType varType = SymbolTable.get(id);

        if (varType == VarType.Bool) {
            m_writer.println("if " + id + " == 1 goto " + ((BoolLabel) data).lTrue);
            m_writer.println("goto " + ((BoolLabel) data).lFalse);
        }

        return id;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }

    public enum VarType {
        Bool,
        Number,
        EnumType,
        EnumVar,
        EnumValue
    }

    private static class BoolLabel {
        public String lTrue;
        public String lFalse;

        public BoolLabel(String lTrue, String lFalse) {
            this.lTrue = lTrue;
            this.lFalse = lFalse;
        }
    }
}
