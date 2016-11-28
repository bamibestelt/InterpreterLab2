import CPP.Absyn.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    ArrayList<Object> inputLine = new ArrayList<>();
    int readerIndex = 0;



    public void interpret(Program p) {
        // define a visitor for the program
        ProgramVisitor programVisitor = new ProgramVisitor();
        // accepting visitor to the program
        p.accept(programVisitor, null);
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
    private Object executeOtherFunction(DFun dFun, ListExp listexp, Environment env) {
        Object returnValue = null;

        // declare new environment for this function scope
        Environment envLocal = new Environment();
        envLocal.setFunctions(env.getFunctions());

        // define a new statement visitor for this function
        StatementVisitor statementVisitor = new StatementVisitor();

        // first, inspect function params
        int i = 0;
        for(Arg arg : dFun.listarg_) {
            Object id = arg.accept(new ArgsVisitor(), envLocal);

            // next, push value to the params variable
            Object exp = listexp.get(i).accept(new ExpressionVisitor(), env);

            if(exp instanceof Integer) {
                envLocal.updateVariable(String.valueOf(id), Integer.valueOf(String.valueOf(exp)));
            } else if(exp instanceof Double) {
                envLocal.updateVariable(String.valueOf(id), Double.valueOf(String.valueOf(exp)));
            } else if(exp instanceof Boolean) {
                envLocal.updateVariable(String.valueOf(id), Boolean.valueOf(String.valueOf(exp)));
            } else {
                envLocal.updateVariable(String.valueOf(id), null);
            }

            i++;
        }

        // visit all statements
        boolean iterate = true;
        Iterator<Stm> iterator = dFun.liststm_.iterator();

        while (iterator.hasNext() && iterate) {
            Object stmValue = iterator.next().accept(statementVisitor, envLocal);

            if(stmValue != null) {
                // because for now we assume that only
                // return statement that results a value
                returnValue = stmValue;
                iterate = false;
            }
        }
        return returnValue;
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
            Exp returnExp = p.exp_;
            Object returnVal = returnExp.accept(new ExpressionVisitor(), env);

            return returnVal;
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
            ListStm listStm = p.liststm_;
            Object value = null;

            Environment envBlock = new Environment();
            envBlock.setFunctions(env.getFunctions());

            LinkedHashMap<String,Variable> importVars = env.getVariables();

            // import all variables from outer scope label: IMPORT
            // then on initialization override the flag to LOCAL
            for(Variable variable : importVars.values()) {
                variable.setVarFlag(Variable.VAR_IMPORT);
                envBlock.addVariable(variable.getVariableId(), variable);
            }

            for(Stm stm : listStm) {
                value = stm.accept(new StatementVisitor(), envBlock);
                if(value != null) {
                    return value;
                }
            }

            for(Variable variable : envBlock.getVariables().values()) {
                if (variable.getVarFlag() == Variable.VAR_IMPORT) {
                    env.addVariable(variable.getVariableId(), variable);
                }
            }

            return value;
        }

        public Object visit(SIfElse p, Environment env) {
            Exp expCondition = p.exp_;
            Stm stmIf = p.stm_1;
            Stm stmElse = p.stm_2;
            Object value = null;

            Object eval = expCondition.accept(new ExpressionVisitor(), env);
            if(eval instanceof Boolean) {
                Boolean condition = Boolean.valueOf(String.valueOf(eval));
                if(condition) {
                    value = stmIf.accept(new StatementVisitor(), env);
                } else {
                    value = stmElse.accept(new StatementVisitor(), env);
                }
            }
            return value;
        }
    }




    /**visitor for expression*/
    public class ExpressionVisitor implements Exp.Visitor<Object, Environment> {

        /**highest expressions: Identifier, Integer, Double, Boolean*/
        // 1 will be interpreted as TRUE for boolean type
        public Object visit(ETrue p, Environment env) {
            return true;
        }

        // 0 will be interpreted as FALSE for boolean type
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

            if(PRINT_INT.equals(p.id_) || PRINT_DOUBLE.equals(p.id_)) {
                printOperation(listExp.getFirst(), env);
                return true;
            } else if(READ_INT.equals(p.id_)) {
                return readOperation(TYPE_INTEGER);
            } else if(READ_DOUBLE.equals(p.id_)) {
                return readOperation(TYPE_DOUBLE);
            } else {
                // other custom functions
                Object funcRetValue = executeOtherFunction(env.getFunction(p.id_), p.listexp_, env);
                return funcRetValue;
            }
        }

        private Object readOperation(String flag) {
            Object echo = null;

            if(inputLine.isEmpty()) {
                Scanner scanner = new Scanner(System.in);
                scanner.useDelimiter("[;\r\n]+");
                while(scanner.hasNext()) {
                    inputLine.add(scanner.next());
                }
            }

            if(flag.equals(TYPE_INTEGER)) {
                echo = Integer.valueOf(String.valueOf(inputLine.get(readerIndex)));
            } else if(flag.equals(TYPE_DOUBLE)) {
                echo = Double.valueOf(String.valueOf(inputLine.get(readerIndex)));
            }

            readerIndex++;
            return echo;
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
            Object object = evaluation(p.exp_, env);

            if(object instanceof Integer) {
                Integer exp = Integer.valueOf(String.valueOf(object));
                ++exp;
                String variable = ((EId) (p.exp_)).id_;
                env.updateVariable(variable, exp);
            } else if(object instanceof Double) {
                Double exp = Double.valueOf(String.valueOf(object));
                ++exp;
                String variable = ((EId) (p.exp_)).id_;
                env.updateVariable(variable, exp);
            }

            return object;
        }

        public Object visit(EPostDecr p, Environment env) {
            Object object = evaluation(p.exp_, env);

            if(object instanceof Integer) {
                Integer exp = Integer.valueOf(String.valueOf(object));
                --exp;
                String variable = ((EId) (p.exp_)).id_;
                env.updateVariable(variable, exp);
            } else if(object instanceof Double) {
                Double exp = Double.valueOf(String.valueOf(object));
                --exp;
                String variable = ((EId) (p.exp_)).id_;
                env.updateVariable(variable, exp);
            }

            return object;
        }

        public Object visit(EPreIncr p, Environment env) {
            Object number = evaluation(p.exp_, env);

            if(number instanceof Integer) {
                Integer exp = Integer.valueOf(String.valueOf(number));
                ++exp;// do pre-increment
                number = exp;
            } else if(number instanceof Double) {
                Double exp = Double.valueOf(String.valueOf(number));
                ++exp;// do pre-increment
                number = exp;
            }

            String variable = ((EId) (p.exp_)).id_;
            env.updateVariable(variable, number);

            return number;
        }

        public Object visit(EPreDecr p, Environment env) {
            Object number = evaluation(p.exp_, env);

            if(number instanceof Integer) {
                Integer exp = Integer.valueOf(String.valueOf(number));
                --exp;// do pre-decrement
                number = exp;
            } else if(number instanceof Double) {
                Double exp = Double.valueOf(String.valueOf(number));
                --exp;// do pre-decrement
                number = exp;
            }

            String variable = ((EId) (p.exp_)).id_;
            env.updateVariable(variable, number);

            return number;
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
                return Double.valueOf(String.valueOf(exp1)).equals(Double.valueOf(String.valueOf(exp2)));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return Integer.valueOf(String.valueOf(exp1)).equals(Integer.valueOf(String.valueOf(exp2)));
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
                return !Double.valueOf(String.valueOf(exp1)).equals(Double.valueOf(String.valueOf(exp2)));
            } else if(exp1 instanceof Integer && exp2 instanceof Integer) {
                return !Integer.valueOf(String.valueOf(exp1)).equals(Integer.valueOf(String.valueOf(exp2)));
            } else if(exp1 instanceof Boolean && exp2 instanceof Boolean) {
                return Boolean.valueOf(String.valueOf(exp1)) != Boolean.valueOf(String.valueOf(exp2));
            } else {
                throw new RuntimeException("!= operator requires Numbers or Boolean");
            }
        }

        public Object visit(EAnd p, Environment env) {
            // lazy evaluation: expression is not evaluated as soon as it is bound to a variable
            // but only when the evaluator is forced to produce the expression's value
            //System.out.println("visit EAnd");
            Object exp1 = evaluation(p.exp_1, env);

            // for && if exp1 is true then needs to evaluate exp2
            // but if exp1 is false, whatever value of exp2 will make entire statement false
            // thus, no need to evaluate exp2
            if(exp1 instanceof Boolean) {
                if(Boolean.valueOf(String.valueOf(exp1))) {
                    Object exp2 = evaluation(p.exp_2, env);
                    if(exp2 instanceof Boolean) {
                        return Boolean.valueOf(String.valueOf(exp1)) && Boolean.valueOf(String.valueOf(exp2));
                    } else {
                        throw new RuntimeException("&& operator requires Boolean");
                    }
                } else {
                    return exp1;
                }
            } else {
                throw new RuntimeException("&& operator requires Boolean");
            }
        }

        public Object visit(EOr p, Environment env) {
            // OR just need exp1 to be true and the rest won't be evaluated
            // since every value from exp2 will result the statement to be true
            //System.out.println("visit EOr");
            Object exp1 = evaluation(p.exp_1, env);

            if(exp1 instanceof Boolean) {
                // if exp1 true then continue evaluate exp2
                if(!Boolean.valueOf(String.valueOf(exp1))) {
                    Object exp2 = evaluation(p.exp_2, env);
                    if(exp2 instanceof Boolean) {
                        return Boolean.valueOf(String.valueOf(exp1)) || Boolean.valueOf(String.valueOf(exp2));
                    } else {
                        throw new RuntimeException("|| operator requires Boolean");
                    }
                } else {
                    return exp1;
                }
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
            return exp2;
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
