import CPP.Absyn.*;
import com.sun.org.apache.xpath.internal.operations.Bool;

import java.util.HashMap;
import java.util.Scanner;
import java.util.TreeMap;

public class Interpreter {

    private final String MAIN_FUNC = "main";

    private final String PRINT_INT = "printInt";
    private final String PRINT_DOUBLE = "printDouble";
    private final String READ_INT = "readInt";
    private final String READ_DOUBLE = "readDouble";

    private final String TYPE_INTEGER = "bool_";
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

        // map of function objects
        private TreeMap<String, DFun> functions = new TreeMap<>();

        // table of variables <var name, variable data>
        private HashMap<String, Variable> variables = new HashMap<>();

        public void setFunctions(TreeMap<String, DFun> inits) {
            this.functions = new TreeMap<>(inits);
        }

        public void addFunction(DFun dFun) {
            functions.put(dFun.id_, dFun);
        }

        public DFun getFunction(String id) {
            return functions.get(id);
        }

        public TreeMap<String, DFun> getFunctions() {
            return functions;
        }

        public void addVariable(String context, Variable var) {
            variables.put(context, var);
        }

        public Variable getVariable(String context) {
            return variables.get(context);
        }

        public Variable removeVariable(String context) {
            return variables.remove(context);
        }

        // value can be many types int, double, boolean
        public void updateVariable(String id, Object value) {
            Variable var = getVariable(id);
            var.setVariableValue(value);
            addVariable(id, var);
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
        // declare new environment for this function scope
        Environment env = new Environment();
        env.setFunctions(environment.getFunctions());

        // define a new statement visitor for this function
        StatementVisitor statementVisitor = new StatementVisitor();

        // first, inspect function params
        int i = 0;
        for(Arg arg : dFun.listarg_) {
            Object id = arg.accept(new ArgsVisitor(), env);

            // next, push value to the params variable
            Object exp = listexp.get(i).accept(new ExpressionVisitor(), env);
            if(exp instanceof Integer) {
                env.updateVariable(String.valueOf(id), Integer.valueOf(String.valueOf(exp)));
            } else if(exp instanceof Double) {
                env.updateVariable(String.valueOf(id), Double.valueOf(String.valueOf(exp)));
            } else if(exp instanceof Boolean) {
                env.updateVariable(String.valueOf(id), Boolean.valueOf(String.valueOf(exp)));
            } else {
                env.updateVariable(String.valueOf(id), null);
            }

            i++;
        }

        // visit all statements
        for (Stm stm: dFun.liststm_) {
            stm.accept(statementVisitor, env);
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
                env.addVariable(variable, new Variable(variable, varTypeString, initVal));
            }

            return null;
        }

        public Object visit(SInit p, Environment env) {
            String variable = p.id_;
            Type varType = p.type_;
            String varTypeString = varType.accept(new TypeVisitor(), null);

            Exp initExp = p.exp_;
            Object initVal = initExp.accept(new ExpressionVisitor(), env);

            env.addVariable(variable, new Variable(variable, varTypeString, initVal));

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
            Exp expCondition = p.exp_;
            Stm stmIf = p.stm_1;
            Stm stmElse = p.stm_2;

            Object eval = expCondition.accept(new ExpressionVisitor(), env);
            if(eval instanceof Boolean) {
                Boolean condition = Boolean.valueOf(String.valueOf(eval));
                if(condition) {
                    stmIf.accept(new StatementVisitor(), env);
                } else {
                    stmElse.accept(new StatementVisitor(), env);
                }
            }

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
            Object eval = evaluation(exp, env);
            if(eval instanceof Integer || eval instanceof Double) {
                System.out.println(String.valueOf(eval));
            } else {
                throw new RuntimeException("Invalid parameter for print method");
            }
        }




        /** pre/post in/decrement */
        public Object visit(EPostIncr p, Environment env) {
            Integer exp = Integer.parseInt(String.valueOf(evaluation(p.exp_, env)));
            Integer post = exp+1;// TODO this post op can be fault

            String variable = ((EId) (p.exp_)).id_;
            env.updateVariable(variable, post);

            return exp;
        }

        public Object visit(EPostDecr p, Environment env) {
            Integer exp = Integer.parseInt(String.valueOf(evaluation(p.exp_, env)));
            Integer post = exp-1;// do post-decrement

            String variable = ((EId) (p.exp_)).id_;
            env.updateVariable(variable, post);

            return exp;
        }

        public Object visit(EPreIncr p, Environment env) {

            Integer exp = Integer.parseInt(String.valueOf(evaluation(p.exp_, env)));
            ++exp;// do pre-increment

            String variable = ((EId) (p.exp_)).id_;
            env.updateVariable(variable, exp);

            return exp;
        }

        public Object visit(EPreDecr p, Environment env) {
            Integer exp = Integer.parseInt(String.valueOf(evaluation(p.exp_, env)));
            --exp;// do pre-increment

            String variable = ((EId) (p.exp_)).id_;
            env.updateVariable(variable, exp);

            return exp;
        }




        /**Arithmetic expression operations*/
        public Object visit(ETimes p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double && exp2 instanceof Double) {
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

            if(exp1 instanceof Double && exp2 instanceof Double) {
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

            if(exp1 instanceof Double && exp2 instanceof Double) {
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

            if(exp1 instanceof Double && exp2 instanceof Double) {
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

            if(exp1 instanceof Double && exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) < Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return Integer.valueOf(String.valueOf(exp1)) < Integer.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("< operator requires Numbers");
            }
        }

        public Object visit(EGt p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double && exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) > Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return Integer.valueOf(String.valueOf(exp1)) > Integer.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("> operator requires Numbers");
            }
        }

        public Object visit(ELtEq p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double && exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) <= Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return Integer.valueOf(String.valueOf(exp1)) <= Integer.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("<= operator requires Numbers");
            }
        }

        public Object visit(EGtEq p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double && exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) >= Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return Integer.valueOf(String.valueOf(exp1)) >= Integer.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException(">= operator requires Numbers");
            }
        }

        public Object visit(EEq p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double && exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) == Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return Integer.valueOf(String.valueOf(exp1)) == Integer.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Boolean && exp2 instanceof Boolean) {
                return Boolean.valueOf(String.valueOf(exp1)) == Boolean.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("== operator requires Numbers or Boolean");
            }
        }

        public Object visit(ENEq p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Double && exp2 instanceof Double) {
                return Double.valueOf(String.valueOf(exp1)) != Double.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return Integer.valueOf(String.valueOf(exp1)) != Integer.valueOf(String.valueOf(exp2));
            } else if(exp1 instanceof Boolean && exp2 instanceof Boolean) {
                return Boolean.valueOf(String.valueOf(exp1)) != Boolean.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("!= operator requires Numbers or Boolean");
            }
        }

        public Object visit(EAnd p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Boolean && exp2 instanceof Boolean) {
                return Boolean.valueOf(String.valueOf(exp1)) && Boolean.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("&& operator requires Boolean");
            }
        }

        public Object visit(EOr p, Environment env) {
            Object exp1 = evaluation(p.exp_1, env);
            Object exp2 = evaluation(p.exp_2, env);

            if(exp1 instanceof Boolean && exp2 instanceof Boolean) {
                return Boolean.valueOf(String.valueOf(exp1)) || Boolean.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("|| operator requires Boolean");
            }
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




    public class ArgsVisitor implements Arg.Visitor<Object, Environment> {

        @Override
        public Object visit(ADecl p, Environment env) {

            String variable = p.id_;
            Type varType = p.type_;
            String varTypeString = varType.accept(new TypeVisitor(), null);

            env.addVariable(variable, new Variable(variable, varTypeString, null));

            return variable;
        }
    }


}
