package M2;

public class Problem4 extends BaseClass {
    private static String[] array1 = { "hello world!", "java programming", "special@#$%^&characters", "numbers 123 456",
            "mIxEd CaSe InPut!" };
    private static String[] array2 = { "hello world", "java programming", "this is a title case test",
            "capitalize every word", "mixEd CASE input" };
    private static String[] array3 = { "  hello   world  ", "java    programming  ",
            "  extra    spaces  between   words   ",
            "      leading and trailing spaces      ", "multiple      spaces" };
    private static String[] array4 = { "hello world", "java programming", "short", "a", "even" };

    private static void transformText(String[] arr, int arrayNumber) {
        // Only make edits between the designated "Start" and "End" comments
        printArrayInfoBasic(arr, arrayNumber);

        // Challenge 1: Remove non-alphanumeric characters except spaces
        // Challenge 2: Convert text to Title Case
        // Challenge 3: Trim leading/trailing spaces and remove duplicate spaces
        // Result 1-3: Assign final phrase to `placeholderForModifiedPhrase`
        // Challenge 4 (extra credit): Extract middle 3 characters (beginning starts at middle of phrase),
        // assign to 'placeholderForMiddleCharacters'
        // if not enough characters assign "Not enough characters"
 
        // Step 1: sketch out plan using comments (include ucid and date)
        // Step 2: Add/commit your outline of comments (required for full credit)
        // Step 3: Add code to solve the problem (add/commit as needed)
        String placeholderForModifiedPhrase = "";
        String placeholderForMiddleCharacters = "";
        
        for(int i = 0; i <arr.length; i++){
            // Start Solution Edits
            
            // UCID: mr822, Date: 06/09/2025
            // Plan:
            // 1. Remove all non-alphanumeric characters (except space) using regex
            // 2. Trim leading/trailing spaces and reduce multiple spaces to one
            // 3. Convert to Title Case by splitting, capitalizing, and joining
            // 4. Assign to placeholderForModifiedPhrase
            // 5. For extra credit: if length >= 3, extract 3 middle characters from cleaned phrase
            //    Else, assign "Not enough characters"

            String cleaned = arr[i].replaceAll("[^a-zA-Z0-9 ]", "");  // remove non-alphanumeric except space
            cleaned = cleaned.trim().replaceAll("\\s+", " ");          // remove extra spaces

            // Title Case: capitalize each word
            String[] words = cleaned.split(" ");
            StringBuilder titleCase = new StringBuilder();
            for (String word : words) {
                if (word.length() > 0) {
                    titleCase.append(Character.toUpperCase(word.charAt(0)));
                    if (word.length() > 1) {
                        titleCase.append(word.substring(1).toLowerCase());
                    }
                    titleCase.append(" ");
                }
            }
            placeholderForModifiedPhrase = titleCase.toString().trim();

            // Extra Credit: Middle 3 characters
            String flat = placeholderForModifiedPhrase.replaceAll(" ", "");
            if (flat.length() >= 3) {
                int mid = flat.length() / 2;
                if (flat.length() % 2 == 1) {
                    placeholderForMiddleCharacters = flat.substring(mid - 1, mid + 2);
                } else {
                    placeholderForMiddleCharacters = flat.substring(mid - 1, mid + 2); // still 3 chars
                }
            } else {
                placeholderForMiddleCharacters = "Not enough characters";
            }


             // End Solution Edits
            System.out.println(String.format("Index[%d] \"%s\" | Middle: \"%s\"",i, placeholderForModifiedPhrase, placeholderForMiddleCharacters));
        }

        
        System.out.println("\n______________________________________");
    }

    public static void main(String[] args) {
        final String ucid = "mr822"; // <-- change to your UCID
        // No edits below this line
        printHeader(ucid, 4);

        transformText(array1, 1);
        transformText(array2, 2);
        transformText(array3, 3);
        transformText(array4, 4);
        printFooter(ucid, 4);
    }

}
