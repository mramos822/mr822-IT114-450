package M3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/*
Challenge 3: Mad Libs Generator (Randomized Stories)
-----------------------------------------------------
- Load a **random** story from the "stories" folder
- Extract **each line** into a collection (i.e., ArrayList)
- Prompts user for each placeholder (i.e., <adjective>) 
    - Any word the user types is acceptable, no need to verify if it matches the placeholder type
    - Any placeholder with underscores should display with spaces instead
- Replace placeholders with user input (assign back to original slot in collection)
*/

public class MadLibsGenerator extends BaseClass {
    private static final String STORIES_FOLDER = "M3/stories";
    private static String ucid = "mr822"; // <-- change to your ucid

    public static void main(String[] args) {
        printHeader(ucid, 3,
                "Objective: Implement a Mad Libs generator that replaces placeholders dynamically.");

        Scanner scanner = new Scanner(System.in);
        File folder = new File(STORIES_FOLDER);

        if (!folder.exists() || !folder.isDirectory() || folder.listFiles().length == 0) {
            System.out.println("Error: No stories found in the 'stories' folder.");
            printFooter(ucid, 3);
            scanner.close();
            return;
        }
        List<String> lines = new ArrayList<>();
        // Start edits

        // UCID: mr822
        // Date: 2025-06-15
        // Loads a random Mad Libs story, prompts for words, replaces placeholders, and prints the result.

        // load a random story file
        File[] storyFiles = folder.listFiles();
        File randomStory = storyFiles[(int) (Math.random() * storyFiles.length)];

        try (Scanner storyScanner = new Scanner(randomStory)) {

        // parse the story lines
            while (storyScanner.hasNextLine()) {
                lines.add(storyScanner.nextLine());
            }
        } catch (Exception e) {
            System.out.println("Error reading story: " + e.getMessage());
            printFooter(ucid, 3);
            scanner.close();
            return;
        }

        // prompt the user for each placeholder (note: there may be more than one
        // placeholder in a line)
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            while (line.contains("<")) {
                int start = line.indexOf('<');
                int end = line.indexOf('>', start);
                if (start == -1 || end == -1) break;

                String placeholder = line.substring(start, end + 1);
                String displayPrompt = placeholder.substring(1, placeholder.length() - 1).replace("_", " ");

                System.out.print("Enter a " + displayPrompt + ": ");
                String userInput = scanner.nextLine();

                line = line.replaceFirst(placeholder, userInput);
            }
            lines.set(i, line);
        }
        // apply the update to the same collection slot
        // End edits
        System.out.println("\nYour Completed Mad Libs Story:\n");
        StringBuilder finalStory = new StringBuilder();
        for (String line : lines) {
            finalStory.append(line).append("\n");
        }
        System.out.println(finalStory.toString());

        printFooter(ucid, 3);
        scanner.close();
    }
}
