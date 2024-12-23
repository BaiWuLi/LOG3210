package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter m_writer;

    private HashMap<String, VarType> SymbolTable = new HashMap<>(); // mapping variable -> type

    // variable pour les metrics
    public int VAR = 0;
    public int WHILE = 0;
    public int IF = 0;
    public int FUNC = 0;
    public int OP = 0;

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
        m_writer.print(String.format("{VAR:%d, WHILE:%d, IF:%d, FUNC:%d, OP:%d}", this.VAR, this.WHILE, this.IF, this.FUNC, this.OP));
        return null;
    }

    // Enregistre les variables avec leur type dans la table symbolique.
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if (SymbolTable.containsKey(varName)) {
            throw new SemantiqueError(String.format("Identifier %s has multiple declarations", varName));
        }

        if (node.getValue().equals("num")) {
            SymbolTable.put(varName, VarType.Number);
            this.VAR++;
        } else {
            SymbolTable.put(varName, VarType.Bool);
            this.VAR++;
        }

        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    // Méthode qui pourrait être utile pour vérifier le type d'expression dans une condition.
    private void callChildenCond(SimpleNode node) {
        DataStruct d = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, d);

        if (d.type != VarType.Bool) {
            throw new SemantiqueError("Invalid type in condition");
        }

        int numChildren = node.jjtGetNumChildren();
        for (int i = 1; i < numChildren; i++) {
            d = new DataStruct();
            node.jjtGetChild(i).jjtAccept(this, d);
        }
    }

    // les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    // On doit aussi compter les conditions dans les variables IF et WHILE
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        this.callChildenCond(node);
        this.IF++;

        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        this.callChildenCond(node);
        this.WHILE++;

        return null;
    }

    @Override
    public Object visit(ASTFunctionStmt node, Object data) {
        node.childrenAccept(this, data);

        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if (SymbolTable.containsKey(varName)) {
            throw new SemantiqueError(String.format("Identifier %s has multiple declarations", varName));
        }

        this.FUNC++;

        return null;
    }

    @Override
    public Object visit(ASTFunctionBlock node, Object data) {
        node.childrenAccept(this, data);

        return null;
    }

    @Override
    public Object visit(ASTReturnStmt node, Object data) {
        DataStruct d = new DataStruct();
        node.childrenAccept(this, d);

        String functionType = ((ASTFunctionStmt) node.jjtGetParent()).getValue();
        String returnType = "";

        if (d.type == VarType.Number) {
            returnType = "num";
        } else if (d.type == VarType.Bool) {
            returnType = "bool";
        }

        if (!functionType.equals(returnType)) {
            throw new SemantiqueError("Return type does not match function type");
        }

        return null;
    }

    // On doit vérifier que le type de la variable est compatible avec celui de l'expression.
    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if (!SymbolTable.containsKey(varName)) {
            throw new SemantiqueError("Invalid use of undefined Identifier " + varName);
        }

        VarType varType = SymbolTable.get(varName);

        DataStruct expr = new DataStruct();
        node.jjtGetChild(1).jjtAccept(this, expr);

        if (varType != expr.type) {
            throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s", varName));
        }

        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);

        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        DataStruct left = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, left);

        String op = node.getValue();

        if (op == null) {
            ((DataStruct) data).type = left.type;

            return null;
        }

        DataStruct right = new DataStruct();
        node.jjtGetChild(1).jjtAccept(this, right);

        if (left.type != right.type) {
            throw new SemantiqueError("Invalid type in expression");
        }

        if (left.type == VarType.Bool && (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">="))) {
            throw new SemantiqueError("Invalid type in expression");
        }

        ((DataStruct) data).type = VarType.Bool;
        this.OP++;

        return null;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        int numChildren = node.jjtGetNumChildren();

        if (numChildren == 1) {
            DataStruct d = new DataStruct();
            node.jjtGetChild(0).jjtAccept(this, d);
            ((DataStruct) data).type = d.type;

            return null;
        }

        for (int i = 0; i < numChildren; i++) {
            DataStruct d = new DataStruct();
            node.jjtGetChild(i).jjtAccept(this, d);

            if (d.type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        ((DataStruct) data).type = VarType.Number;
        this.OP += numChildren - 1;

        return null;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        int numChildren = node.jjtGetNumChildren();

        if (numChildren == 1) {
            DataStruct d = new DataStruct();
            node.jjtGetChild(0).jjtAccept(this, d);
            ((DataStruct) data).type = d.type;

            return null;
        }

        for (int i = 0; i < numChildren; i++) {
            DataStruct d = new DataStruct();
            node.jjtGetChild(i).jjtAccept(this, d);

            if (d.type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        ((DataStruct) data).type = VarType.Number;
        this.OP += numChildren - 1;

        return null;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        int numChildren = node.jjtGetNumChildren();

        if (numChildren == 1) {
            DataStruct d = new DataStruct();
            node.jjtGetChild(0).jjtAccept(this, d);
            ((DataStruct) data).type = d.type;

            return null;
        }

        for (int i = 0; i < numChildren; i++) {
            DataStruct d = new DataStruct();
            node.jjtGetChild(i).jjtAccept(this, d);

            if (d.type != VarType.Bool) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        ((DataStruct) data).type = VarType.Bool;
        this.OP += numChildren - 1;

        return null;
    }

    @Override
    public Object visit(ASTNotExpr node, Object data) {
        int numOps = node.getOps().size();
        DataStruct d = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, d);

        if (numOps > 0 && d.type != VarType.Bool) {
            throw new SemantiqueError("Invalid type in expression");
        }

        ((DataStruct) data).type = d.type;
        this.OP += numOps;

        return null;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        int numOps = node.getOps().size();
        DataStruct d = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, d);

        if (numOps > 0 && d.type != VarType.Number) {
            throw new SemantiqueError("Invalid type in expression");
        }

        ((DataStruct) data).type = d.type;
        this.OP += numOps;

        return null;
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }


    @Override
    public Object visit(ASTBoolValue node, Object data) {
        ((DataStruct) data).type = VarType.Bool;
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {

        if (node.jjtGetParent() instanceof ASTGenValue) {
            String varName = node.getValue();
            VarType varType = SymbolTable.get(varName);

            ((DataStruct) data).type = varType;
        }

        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        ((DataStruct) data).type = VarType.Number;
        return null;
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

    }
}
