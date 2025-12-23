package M3;

/*
Challenge 1: Command-Line Calculator
------------------------------------
- Accept two numbers and an operator as command-line arguments
- Supports addition (+) and subtraction (-)
- Allow integer and floating-point numbers
- Ensures correct decimal places in output based on input (e.g., 0.1 + 0.2 → 1 decimal place)
- Display an error for invalid inputs or unsupported operators
- Capture 5 variations of tests
*/


public class CommandLineCalculator extends BaseClass {
    private static String ucid = "mr822"; // <-- change to your ucid

    public static void main(String[] args) {
        // UCID: mr822
        // Date: 2025-06-15
        // Solves command-line calculator by parsing args and handling + and - for int and float.

        printHeader(ucid, 1, "Objective: Implement a calculator using command-line arguments.");

        if (args.length != 3) {
            System.out.println("Usage: java M3.CommandLineCalculator <num1> <operator> <num2>");
            printFooter(ucid, 1);
            return;
        }

        try {
            System.out.println("Calculating result...");
            // extract the equation (format is <num1> <operator> <num2>)

            // check if operator is addition or subtraction

            // check the type of each number and choose appropriate parsing

            // generate the equation result (Important: ensure decimals display as the
            // longest decimal passed)
            // i.e., 0.1 + 0.2 would show as one decimal place (0.3), 0.11 + 0.2 would shows
            // as two (0.31), etc

            String num1Str = args[0];
            String operator = args[1];
            String num2Str = args[2];

            boolean isFloat = num1Str.contains(".") || num2Str.contains(".");

            double num1 = Double.parseDouble(num1Str);
            double num2 = Double.parseDouble(num2Str);
            double result;

            if (operator.equals("+")) {
                result = num1 + num2;
            } else if (operator.equals("-")) {
                result = num1 - num2;
            } else {
                System.out.println("Unsupported operator. Use + or -");
                printFooter(ucid, 1);
                return;
            }

            int decimals = Math.max(getDecimalPlaces(num1Str), getDecimalPlaces(num2Str));
            String format = "%." + decimals + "f";

            System.out.println(num1Str + " " + operator + " " + num2Str + " = " + String.format(format, result));

        } catch (Exception e) {
            System.out.println("Invalid input. Please ensure correct format and valid numbers.");
        }

        printFooter(ucid, 1);
    }

    private static int getDecimalPlaces(String numStr) {
        int index = numStr.indexOf('.');
        if (index == -1) return 0;
        return numStr.length() - index - 1;
    }
}
