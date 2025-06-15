package M3;

/*
Challenge 2: Simple Slash Command Handler
-----------------------------------------
- Accept user input as slash commands
  - "/greet <name>" → Prints "Hello, <name>!"
  - "/roll <num>d<sides>" → Roll <num> dice with <sides> and returns a single outcome as "Rolled <num>d<sides> and got <result>!"
  - "/echo <message>" → Prints the message back
  - "/quit" → Exits the program
- Commands are case-insensitive
- Print an error for unrecognized commands
- Print errors for invalid command formats (when applicable)
- Capture 3 variations of each command except "/quit"
*/

import java.util.Scanner;

public class SlashCommandHandler extends BaseClass {
    private static String ucid = "mr822"; // <-- your UCID

    public static void main(String[] args) {

        // UCID: mr822
        // Date: 2025-06-15
        // Handles user slash commands: /greet, /roll, /echo, /quit with error checks.

        printHeader(ucid, 2, "Objective: Implement a simple slash command parser.");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter command: ");
            String input = scanner.nextLine().trim();
            String lower = input.toLowerCase();

            if (lower.startsWith("/greet ")) {
                String name = input.substring(7).trim();
                if (!name.isEmpty()) {
                    System.out.println("Hello, " + name + "!");
                } else {
                    System.out.println("Usage: /greet <name>");
                }

            } else if (lower.startsWith("/roll ")) {
                try {
                    String dicePart = input.substring(6).trim();
                    String[] parts = dicePart.toLowerCase().split("d");
                    if (parts.length == 2) {
                        int numDice = Integer.parseInt(parts[0]);
                        int sides = Integer.parseInt(parts[1]);
                        int total = 0;
                        for (int i = 0; i < numDice; i++) {
                            total += (int) (Math.random() * sides) + 1;
                        }
                        System.out.println("Rolled " + numDice + "d" + sides + " and got " + total + "!");
                    } else {
                        System.out.println("Usage: /roll <num>d<sides>");
                    }
                } catch (Exception e) {
                    System.out.println("Invalid /roll format. Use: /roll 2d6");
                }

            } else if (lower.startsWith("/echo ")) {
                String msg = input.substring(6).trim();
                if (!msg.isEmpty()) {
                    System.out.println(msg);
                } else {
                    System.out.println("Usage: /echo <message>");
                }

            } else if (lower.equals("/quit")) {
                System.out.println("Goodbye!");
                break;

            } else {
                System.out.println("Unknown command. Try /greet, /roll, /echo, or /quit.");
            }
        }

        printFooter(ucid, 2);
        scanner.close();
    }
}
