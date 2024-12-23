package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.HashMap;

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter m_writer;

    public HashMap<String, VarType> SymbolTable = new HashMap<>();

    public SemantiqueVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, data);
        m_writer.print("all good");
        return data;
    }

    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);
        VarType t;
        if (node.getValue().equals("bool")) {
            t = VarType.Bool;
        } else {
            t = VarType.Number;
        }
        SymbolTable.put(id.getValue(), t);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }

    @Override
    public Object visit(ASTEnumStmt node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }

    @Override
    public Object visit(ASTBreakStmt node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTIfStmt node, Object data) {
        DataStruct firstD = (DataStruct) node.jjtGetChild(0).jjtAccept(this, data);
        if (!estCompatible(firstD.type, VarType.Bool)) {
            throw new SemantiqueError("Invalid type in condition");
        }
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }

        return data;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        DataStruct firstD = (DataStruct) node.jjtGetChild(0).jjtAccept(this, data);
        if (!estCompatible(firstD.type, VarType.Bool)) {
            throw new SemantiqueError("Invalid type in condition");
        }
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }

        return data;
    }

    @Override
    public Object visit(ASTForStmt node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        DataStruct assignId = (DataStruct) node.jjtGetChild(0).jjtAccept(this, data);
        DataStruct assignExpr = (DataStruct) node.jjtGetChild(1).jjtAccept(this, data);
        if (!estCompatible(assignId.type, assignExpr.type)) {
            throw new SemantiqueError("Invalid type in assignment");
        }
        return data;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {

        return visitExprAst(node, data, VarType.Bool);
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        DataStruct firstD = (DataStruct) node.jjtGetChild(0).jjtAccept(this, data);
        String op = node.getValue();

        VarType expectedType = VarType.Number;

        VarType newType = firstD.type;
        if (op != null) {
            if (op.equals("==") || op.equals("!=")) {
                expectedType = firstD.type;

            }
            newType = VarType.Bool;
        }

        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            DataStruct d = (DataStruct) node.jjtGetChild(i).jjtAccept(this, data);
            firstD.checkType(d, expectedType);
        }
        firstD.type = newType;
        return firstD;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return visitExprAst(node, data, VarType.Number);
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return visitExprAst(node, data, VarType.Number);
    }


    //Unary operator
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        Boolean haveOp = node.getOps().size() > 0;

        DataStruct firstD = (DataStruct) node.jjtGetChild(0).jjtAccept(this, data);

        if (haveOp) {
            firstD.checkType(VarType.Bool);
        }
        return firstD;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        Boolean haveOp = node.getOps().size() > 0;

        DataStruct firstD = (DataStruct) node.jjtGetChild(0).jjtAccept(this, data);

        if (haveOp) {
            firstD.checkType(VarType.Number);
        }
        return firstD;
    }

    private DataStruct visitExprAst(SimpleNode node, Object data, VarType expectedType) {
        DataStruct firstD = (DataStruct) node.jjtGetChild(0).jjtAccept(this, data);

        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            DataStruct d = (DataStruct) node.jjtGetChild(i).jjtAccept(this, data);
            firstD.checkType(d, expectedType);
        }
        return firstD;
    }


    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }


    @Override
    public Object visit(ASTBoolValue node, Object data) {
        DataStruct d = new DataStruct(VarType.Bool);
        return d;
    }


    @Override
    public Object visit(ASTIdentifier node, Object data) {
        DataStruct d = new DataStruct();

        if (data == null || !data.equals("declaration")) {
            if (SymbolTable.get(node.getValue()) != null) {
                d.type = SymbolTable.get(node.getValue());
            } else {
                throw new SemantiqueError("Invalid use of undefined Identifier " + node.getValue());
            }
        }
        return d;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return new DataStruct(VarType.Number);
    }

    private boolean estCompatible(VarType a, VarType b) {
        return a == b;
    }

    public enum VarType {
        Bool,
        Number
    }

    private class DataStruct {
        public VarType type;

        public DataStruct() {
        }

        public DataStruct(VarType p_type) {
            type = p_type;
        }

        public void checkType(VarType expectedType) {
            if (!estCompatible(type, expectedType)) {
                throw new SemantiqueError("Invalid type in expression got " + type.toString() + " was expecting " + expectedType);
            }
        }

        public void checkType(DataStruct d, VarType expectedType) {
            if (!estCompatible(type, expectedType) || !estCompatible(d.type, expectedType)) {
                throw new SemantiqueError("Invalid type in expression got " + type.toString() + " and " + d.type.toString() + " was expecting " + expectedType);
            }
        }
    }
}
