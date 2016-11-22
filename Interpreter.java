import CPP.Absyn.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;

public class Interpreter {

    private final String MAIN_FUNC = "main";

    private final String PRINT_INT = "printInt";
    private final String PRINT_DOUBLE = "printDouble";
    private final String READ_INT = "readInt";
    private final String READ_DOUBLE = "readDouble";

    private final String TYPE_INTEGER = "integer";
    private final String TYPE_DOUBLE = "double";
    private final String TYPE_BOOLEAN = "boolean";
    private final String TYPE_VOID = "void";

    // declare an environment for the whole program
    Environment environment = new Environment();




    public void interpret(Program p) {
        //throw new RuntimeException("Not yet an interpreter");

        // define a visitor for the program
        ProgramVisitor programVisitor = new ProgramVisitor();
        // accepting visitor to the program
        p.accept(programVisitor, null);
    }




    private class Environment {

        public Environment() {}

        // list of function objects
        private LinkedList<DFun> functions = new LinkedList<>();

        // table of variables <var name, variable data>
        private HashMap<String, Variable> variables = new HashMap<>();

        public void addFunction(DFun dFun) {
            functions.add(dFun);
        }

        public DFun getFunction(int index) {
            return functions.get(index);
        }

        public DFun getFunction(String id) {
            DFun fun = null;
            for(DFun dFun: getFunctionList()) {
                if((dFun.id_).equals(id)) {
                    fun = dFun;
                }
            }
            return fun;
        }

        public LinkedList<DFun> getFunctionList() {
            return functions;
        }

        public void setVariable(String context, Variable var) {
            variables.put(context, var);
        }

        public Variable getVariable(String context) {
            return variables.get(context);
        }

        // value can be many types int, double, boolean
        public void updateVariable(String id, Object value) {
            Variable var = getVariable(id);
            var.setVariableValue(value);
            setVariable(id, var);
        }

    }



    public class Variable {

        private String variableId;
        private String variableType;
        private Object variableValue;

        public Variable() {}

        public Variable(String id, String type, Object value) {
            this.variableId = id;
            this.variableType = type;
            this.variableValue = value;
        }

        public String getVariableId() {
            return variableId;
        }

        public void setVariableId(String variableId) {
            this.variableId = variableId;
        }

        public String getVariableType() {
            return variableType;
        }

        public void setVariableType(String variableType) {
            this.variableType = variableType;
        }

        public Object getVariableValue() {
            return variableValue;
        }

        public void setVariableValue(Object variableValue) {
            this.variableValue = variableValue;
        }
    }




    public class ProgramVisitor<R,A> implements Program.Visitor<R,A> {

        public R visit(CPP.Absyn.PDefs p, A arg) {
            // declare a new visitor for definition
            DefinitionVisitor definitionVisitor = new DefinitionVisitor();

            // iterating over each of the list of definition's item
            // in this case function is the only type of definition
            for (Def def: p.listdef_) {
                // accept visitor for this definition
                // this visit is just to gather all functions info
                def.accept(definitionVisitor, null);
            }

            DFun funMain = environment.getFunction(MAIN_FUNC);
            executeMain(funMain);

            return null;
        }
    }


    /**this is the execution method
     * for the top-level function main()*/
    private void executeMain(DFun dFun) {
        // define a new statement visitor for main()
        StatementVisitor statementVisitor = new StatementVisitor();

        // function main() doesn't have params
        // so just iterate over all statements
        for (Stm stm: dFun.liststm_) {
            stm.accept(statementVisitor, environment);
        }
    }


    /**this is the execution method
     * for functions other than main*/
    private void executeOtherFunction(DFun dFun, ListExp listexp) {
        // define a new statement visitor for this function
        StatementVisitor statementVisitor = new StatementVisitor();

        // TODO first, inspect function params

        // visit all statements
        for (Stm stm: dFun.liststm_) {
            stm.accept(statementVisitor, environment);
        }
    }




    /**Collecting all external definitions into the environment*/
    public class DefinitionVisitor<R,A> implements Def.Visitor<R,A> {

        public R visit(CPP.Absyn.DFun p, A arg) {

            // first the interpreter must gather all
            // the function definitions to the Environment
            environment.addFunction(p);

            return null;
        }
    }




    public class StatementVisitor implements Stm.Visitor<Object, Environment> {

        /**
         * for expression, there are 4 built-in functions
         * for function call Exp15
         * void printInt(int x);void printDouble(double x);
         * int readInt();double readDouble();
         * */
        public Object visit(SExp p, Environment env) {
            ExpressionVisitor expressionVisitor = new ExpressionVisitor();
            Exp exp = p.exp_;
            exp.accept(expressionVisitor, env);

            return null;
        }

        public Object visit(SDecls p, Environment env) {
            Type varsType = p.type_;
            String varTypeString = varsType.accept(new TypeVisitor(), null);

            Object initVal = null;
            if(varsType instanceof Type_bool) {
                initVal = false;
            } else if(varsType instanceof Type_int) {
                initVal = 0;
            } else if(varsType instanceof Type_double) {
                initVal = 0.0;
            } else if(varsType instanceof Type_void) {

            } else {
                throw new RuntimeException("Unknown declaration type: "+varTypeString);
            }

            for(String variable : p.listid_) {
                env.setVariable(variable, new Variable(variable, varTypeString, initVal));
            }

            return null;
        }

        public Object visit(SInit p, Environment env) {
            String variable = p.id_;
            Type varType = p.type_;
            String varTypeString = varType.accept(new TypeVisitor(), null);

            Exp initExp = p.exp_;
            Object initVal = initExp.accept(new ExpressionVisitor(), null);

            env.setVariable(variable, new Variable(variable, varTypeString, initVal));

            return null;
        }

        public Object visit(SReturn p, Environment env) {
            return null;
        }

        public Object visit(SWhile p, Environment env) {
            Exp expCondition = p.exp_;
            Stm stm = p.stm_; // can be block statement

            whileStatementOps(expCondition, stm, env);

            return null;
        }

        private void whileStatementOps(Exp expCondition, Stm stm, Environment env) {
            String boolString = String.valueOf(expCondition.accept(new ExpressionVisitor(), env));

            boolean state = Boolean.valueOf(boolString);

            if(state) {
                stm.accept(new StatementVisitor(), env);
                whileStatementOps(expCondition, stm, env);
            }
        }

        public Object visit(SBlock p, Environment env) {
            // TODO be aware of initialization block statement
            ListStm listStm = p.liststm_;

            for(Stm stm : listStm) {
                stm.accept(new StatementVisitor(), env);
            }

            return null;
        }

        public Object visit(SIfElse p, Environment env) {
            return null;
        }
    }




    /**visitor for expression*/
    public class ExpressionVisitor implements Exp.Visitor<Object, Environment> {

        /**highest expressions: Identifier, Integer, Double, Boolean*/
        // TODO 1 will be interpreted as TRUE for boolean type
        public Object visit(ETrue p, Environment env) {
            return true;
        }

        // TODO 0 will be interpreted as FALSE for boolean type
        public Object visit(EFalse p, Environment env) {
            return false;
        }

        public Object visit(EInt p, Environment env) {
            return p.integer_;
        }

        public Object visit(EDouble p, Environment env) {
            return p.double_;
        }

        public Object visit(EId p, Environment env) {
            Object value = env.getVariable(p.id_).getVariableValue();

            return value;
        }




        /** handles all function call expressions inside statement */
        public Object visit(EApp p, Environment env) {
            // get all the argument params
            ListExp listExp = p.listexp_;

            if(PRINT_INT.equals(p.id_)) {
                printOperation(listExp.getFirst(), env);

            } else if(PRINT_DOUBLE.equals(p.id_)) {

            } else if(READ_INT.equals(p.id_)) {
                return readIntOperation();

            } else if(READ_DOUBLE.equals(p.id_)) {
                return readDoubleOperation();

            } else {
                // other custom functions
                executeOtherFunction(env.getFunction(p.id_), p.listexp_);
            }

            return null;
        }

        private Object readIntOperation() {
            Scanner scanner = new Scanner(System.in);
            String echo = scanner.next();
            Integer echoInt = Integer.valueOf(echo);

            return echoInt;
        }

        private Object readDoubleOperation() {
            Scanner scanner = new Scanner(System.in);
            String echo = scanner.next();
            Double echoDouble = Double.valueOf(echo);

            return echoDouble;
        }

        private void printOperation(Exp exp, Environment env) {
            if(exp instanceof EId) {
                // the param is integer
                System.out.println(env.getVariable(((EId) exp).id_).getVariableValue());

            } else if(exp instanceof EInt) {
                // the param is variable
                System.out.println(((EInt) exp).integer_);

            } else if(exp instanceof EDouble) {
                // the param is double
                System.out.println(((EDouble) exp).double_);

            } else if(exp instanceof EApp) {
                // the param is another method
                System.out.println("not yet defined");

            }
        }




        /** pre/post in/decrement */
        public Object visit(EPostIncr p, Environment env) {
            return null;
        }

        public Object visit(EPostDecr p, Environment env) {
            return null;
        }

        public Object visit(EPreIncr p, Environment env) {

            Integer exp = Integer.parseInt(String.valueOf(evaluation(p.exp_, env)));
            ++exp;// do pre-increment

            String variable = ((EId) (p.exp_)).id_;
            env.updateVariable(variable, exp);

            return exp;
        }

        public Object visit(EPreDecr p, Environment env) {
            return null;
        }




        /**Arithmetic expression operations*/
        public Object visit(ETimes p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double || exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) * Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return (Integer) exp1 * (Integer) exp2;
            } else {
                throw new RuntimeException("Arithmetic operation requires Numbers");
            }
        }

        public Object visit(EDiv p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double || exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) / Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return (Integer) exp1 / (Integer) exp2;
            } else {
                throw new RuntimeException("Arithmetic operation requires Numbers");
            }
        }

        public Object visit(EPlus p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double || exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) + Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return (Integer) exp1 + (Integer) exp2;
            } else {
                throw new RuntimeException("Arithmetic operation requires Numbers");
            }
        }

        public Object visit(EMinus p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double || exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) - Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return (Integer) exp1 - (Integer) exp2;
            } else {
                throw new RuntimeException("Arithmetic operation requires Numbers");
            }
        }




        /**boolean operator expressions*/
        public Object visit(ELt p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            // TODO be careful with FP numbers for comparing
            if(exp1 instanceof Double || exp1 instanceof Integer ||
                    exp2 instanceof Double || exp2 instanceof Integer) {
                return Double.valueOf(String.valueOf(exp1)) < Double.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("< operator requires Numbers");
            }
        }

        public Object visit(EGt p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double || exp1 instanceof Integer ||
                    exp2 instanceof Double || exp2 instanceof Integer) {
                return Double.valueOf(String.valueOf(exp1)) > Double.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("> operator requires Numbers");
            }
        }

        public Object visit(ELtEq p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double || exp1 instanceof Integer ||
                    exp2 instanceof Double || exp2 instanceof Integer) {
                return Double.valueOf(String.valueOf(exp1)) <= Double.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("<= operator requires Numbers");
            }
        }

        public Object visit(EGtEq p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double || exp1 instanceof Integer ||
                    exp2 instanceof Double || exp2 instanceof Integer) {
                return Double.valueOf(String.valueOf(exp1)) >= Double.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException(">= operator requires Numbers");
            }
        }

        public Object visit(EEq p, Environment env) {
            return null;
        }

        public Object visit(ENEq p, Environment env) {
            return null;
        }

        public Object visit(EAnd p, Environment env) {
            return null;
        }

        public Object visit(EOr p, Environment env) {
            return null;
        }




        /**Assignment expression*/
        public Object visit(EAss p, Environment env) {

            String variable = ((EId) (p.exp_1)).id_;
            // can be int, double, boolean, void
            Object exp2 = evaluation(p.exp_2, env);

            env.updateVariable(variable, exp2);

            return null;
        }

    }


    /**evaluation method to evaluate expression*/
    public Object evaluation(Exp exp, Environment env) {
        return exp.accept(new ExpressionVisitor(), env);
    }





    /**just to identify types of variables, functions etc*/
    public class TypeVisitor implements Type.Visitor<String, Environment> {

        @Override
        public String visit(Type_bool p, Environment arg) {
            return TYPE_BOOLEAN;
        }

        @Override
        public String visit(Type_int p, Environment arg) {
            return TYPE_INTEGER;
        }

        @Override
        public String visit(Type_double p, Environment arg) {
            return TYPE_DOUBLE;
        }

        @Override
        public String visit(Type_void p, Environment arg) {
            return TYPE_VOID;
        }
    }


}
