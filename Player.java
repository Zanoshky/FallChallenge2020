import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * TODO Agreesive on first 15 turns to learn all positive spells 4000 1100 0200
 *
 * TODO Repeatable are not taken into account
 *
 * Boss
 *
 * TODO:
 *
 * [ ] Optimize BEST_SPELLS and its orders
 *
 * [x] Implement after finishing 4th potion, finish ASAP next two
 *
 * [x] Sometimes timeout seed=8506911812828014600
 *
 * [x] Sometimes stuck and does nothing seed=8506911812828014600
 *
 * [x] Make it automatically to decide 8 - 10 rounds how long to learn
 *
 * [x] Always take first positive spell even if i am over limit
 */

class Player {

    private static final int[] NO_INGREDIENT_COST = new int[4];

    // ALGORITHM ADJUSTMENT ---------------------------------

    //    private static final int ZAI_DEEP_SEARCH_TIME_IN_MS = 8;
    //    private static final int ZAI_DEEP_SEARCH_NODES = 7;
    //    private static final int ZAI_LEARN_UNTIL_ROUND = 9;
    //    private static final int ZAI_FINISH_ASAP_IF_I_HAVE_POTIONS = 5; // Ignore if over 5
    //    private static final int ZAI_FINISH_ASAP_IF_ENEMY_HAS_POTIONS = 5; // Ignore if over 5
    //    private static final double ZAI_PRICE_SENSITIVITY_PATH_DECIDE = 2.5;

    private static final int ZAI_DEEP_SEARCH_TIME_IN_NS = 8_500_000;
    private static final int ZAI_DEEP_SEARCH_STACK_SIZE = 5000;
    private static final int ZAI_DEEP_SEARCH_NODES = 7;
    private static final int ZAI_LEARN_UNTIL_ROUND = 9;
    private static final int ZAI_FINISH_ASAP_IF_I_HAVE_POTIONS = 4; // Ignore if over 5
    private static final int ZAI_FINISH_ASAP_IF_ENEMY_HAS_POTIONS = 4; // Ignore if over 5
    private static final double ZAI_PRICE_SENSITIVITY_PATH_DECIDE = 2.5;

    // ------------------------------------------------------
    private static final Rest ACTION_REST = new Rest();
    private static final String ACTION_CAST = "CAST";
    private static final String ACTION_BREW = "BREW";
    private static final String ACTION_LEARN = "LEARN";
    private static final String ACTION_OPPONENT_CAST = "OPPONENT_CAST";

    private static int POTIONS_SOLD_ME = 0;
    private static int SCORE_ME = 0;

    private static int POTION_SOLD_ENEMY = 0;
    private static int SCORE_ENEMY = 0;

    // -----------------------------

    /**
     * Main function which executed on CodinGame Servers. It is important that the top class name is Player otherwise it wont run.
     *
     * The code reads in X amount of actions from InputStream (file), and simulates 1 round of the game by returning the desired ( aka i think the best) action for the round.
     *
     * The game goes in turns red blue red blue, so no one is at disadvantage, as it is random order to start from.
     */
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        int gameRound = 1;

        // game loop
        while (true) {
            String command = simulateRound(in, gameRound);
            System.out.println(command);
            gameRound++;
        }
    }

    /**
     * Extracted the logic of reading the input to method, so it can be jUnit Tested. Please look at tests for example.
     */
    public static String simulateRound(Scanner in, int gameRound) {
        int actionCount = in.nextInt();

        List<Spell> spells = new ArrayList<>();
        List<Potion> potions = new ArrayList<>();
        List<TomeSpell> tomeSpells = new ArrayList<>();
        List<Spell> enemySpells = new ArrayList<>(0);

        readGameRoundState(in, actionCount, spells, potions, tomeSpells);

        Inventory inventory = getInventory(in);
        Inventory enemyInv = getInventory(in);

        if (inventory.getScore() != SCORE_ME) {
            SCORE_ME = inventory.getScore();
            POTIONS_SOLD_ME++;
        }

        if (enemyInv.getScore() != SCORE_ENEMY) {
            SCORE_ENEMY = enemyInv.getScore();
            POTION_SOLD_ENEMY++;
        }

        return decideNextAction(spells, potions, tomeSpells, inventory, gameRound);
    }

    /**
     * The heart of the AI, it decides what is the best next action from the given game state of spells, potions, tome spells, and current inventory.
     *
     * There is also extra data available like opponent spells and opponent inventory, but the current version is ignoring it.
     */
    public static String decideNextAction(List<Spell> spells, List<Potion> potions, List<TomeSpell> tomeSpells, Inventory inventory, int gameRound) {
        if (gameRound <= ZAI_LEARN_UNTIL_ROUND) {
            if (tomeSpells.get(0).isAllPositive()) {
                return tomeSpells.get(0).getAction();
            } else if (tomeSpells.get(1).isAllPositive() && inventory.getIngredients()[0] > 0) {
                return tomeSpells.get(1).getAction();
            }

            return tomeSpells.get(0).getAction();
        } else if (tomeSpells.get(0).isAllPositive()) {
            return tomeSpells.get(0).getAction();
        } else if (tomeSpells.get(1).isAllPositive() && inventory.getIngredients()[0] > 0) {
            return tomeSpells.get(1).getAction();
        }

        PriorityQueue<Node> bestPossibleNodesForTarget = new PriorityQueue<>();
        for (Potion aStarGoalPotion : potions) {
            long startTime = System.nanoTime();
            List<Action> allActions = new ArrayList<>();
            allActions.add(aStarGoalPotion); // Add target potion where A* Should reach
            allActions.addAll(spells); // Add all spells
            Node bestNode = findBestNode(inventory.getIngredients(), allActions, aStarGoalPotion);

            if (bestNode != null) {
                bestPossibleNodesForTarget.add(bestNode);
            }

            System.err.println("Potion: " + aStarGoalPotion + " - " + bestNode + " - Took: " + ((double) (System.nanoTime() - startTime) / 1_000_000L) + " ms");
        }

        // Best node
        Node bestNode = bestPossibleNodesForTarget.poll();

        // If all nodes are null
        if (bestNode == null) {
            Optional<Spell> firstBestAction = spells.stream()
                    .filter(action -> action.isActionCastable() && canUseAction(inventory.getIngredients(), action.getIngredientCost()))
                    .sorted((a1, a2) -> ThreadLocalRandom.current().nextInt(-1, 2))
                    .findAny();

            if (firstBestAction.isPresent()) {
                System.err.println("RETURN Random Action:" + firstBestAction.get().getAction());
                return firstBestAction.get().getAction();
            }

            System.err.println("RETURN Random Action: REST");
            return ACTION_REST.getAction();
        }

        return bestNode.getFirstAction().getAction();
    }

    public static void readGameRoundState(Scanner in, int actionCount, List<Spell> spells, List<Potion> potions, List<TomeSpell> tomeSpells) {
        for (int i = 0; i < actionCount; i++) {
            int actionId = in.nextInt();
            String actionType = in.next();
            int d0 = in.nextInt();
            int d1 = in.nextInt();
            int d2 = in.nextInt();
            int d3 = in.nextInt();
            int price = in.nextInt();
            int tomeIndex = in.nextInt();
            int taxCount = in.nextInt();
            int castable = in.nextInt();
            int repeatable = in.nextInt();

            int[] ingredientCost = new int[]{d0, d1, d2, d3};

            if (ACTION_CAST.equalsIgnoreCase(actionType)) {
                spells.add(new Spell(actionId, ingredientCost, price, tomeIndex, taxCount, castable, repeatable, 0));
            } else if (ACTION_BREW.equalsIgnoreCase(actionType)) {
                potions.add(new Potion(actionId, ingredientCost, price, tomeIndex, taxCount, castable, repeatable));
            } else if (ACTION_LEARN.equalsIgnoreCase(actionType)) {
                tomeSpells.add(new TomeSpell(actionId, ingredientCost, price, tomeIndex, taxCount, castable, repeatable));
            }
        }
    }

    private static Inventory getInventory(Scanner in) {
        int inv0 = in.nextInt();
        int inv1 = in.nextInt();
        int inv2 = in.nextInt();
        int inv3 = in.nextInt();
        int score = in.nextInt();

        int[] ingredients = new int[]{inv0, inv1, inv2, inv3};

        return new Inventory(ingredients, score);
    }

    /**
     * Checks if you can use action, by comparing if you have enough ingredients in your inventory for the action ingredient cost. And then checks if you still will have under or 10 elements so you
     * dont go over the limit
     */
    private static boolean canUseAction(int[] inventory, int[] actionCost) {
        int spaceAfterCommandUse = 0;

        for (int i = 0; i < 4; i++) {
            spaceAfterCommandUse += inventory[i] + actionCost[i];

            if (inventory[i] + actionCost[i] < 0) {
                return false;
            }
        }

        return spaceAfterCommandUse <= 10;
    }

    /**
     * Checks if you can use action, by comparing if you have enough ingredients in your inventory for the action ingredient cost. And then checks if you still will have under or 10 elements so you
     * dont go over the limit
     */
    private static boolean canLearnTomeSpell(int[] inventory, Action action) {
        if (action instanceof TomeSpell) {
            return action.getTomeIndex() <= inventory[0];
        }

        return false;
    }

    private static Node findBestNode(int[] inventory, List<Action> actions, Potion targetedPotion) {
        PriorityQueue<Node> nodeStack = new PriorityQueue<>();
        Node node = new Node(inventory, actions, Integer.MAX_VALUE);

        long underTime = 1;
        long start = System.nanoTime();

        while (underTime < ZAI_DEEP_SEARCH_TIME_IN_NS && !containsDesiredAction(node, targetedPotion)) {
            if (isNodeEligibleForSearch(node)) {
                for (Action action : node.getAvailableActions()) {
                    underTime = System.nanoTime() - start;
                    if (underTime >= ZAI_DEEP_SEARCH_TIME_IN_NS) {
                        break;
                    }

                    if (canUseActionWithInventoryState(node, action)) {
                        Node tempNode = node;
                        int repeatable = node.howManyTimeIsActionRepeatable(action);

                        if (!node.isActionCastable(action)) {
                            tempNode = node.addNewNode(ACTION_REST, 0);
                        }

                        if (repeatable == 0) {
                            tempNode = tempNode.addNewNode(action, 0);

                            if (tempNode == null) {
                                continue;
                            }

                            nodeStack.add(tempNode);
                        } else if (repeatable > 0) {
                            for (int i = 1; i <= repeatable + 1; i++) {

                                underTime = System.nanoTime() - start;
                                if (underTime >= ZAI_DEEP_SEARCH_TIME_IN_NS) {
                                    break;
                                }

                                Node cloneNode = tempNode.clone();
                                cloneNode = cloneNode.addNewNode(action, i);

                                if (cloneNode == null) {
                                    break;
                                }

                                nodeStack.add(cloneNode);
                            }
                        }
                    }
                }
            }

            node = nodeStack.poll();

            if (node == null) {
                return null;
            }

            if (nodeStack.size() > ZAI_DEEP_SEARCH_STACK_SIZE) {
                return node;
            }

            underTime = System.nanoTime() - start;
        }

        return node;
    }

    private static boolean canUseActionWithInventoryState(Node node, Action action) {
        return canLearnTomeSpell(node.getInventory(), action) || (!(action instanceof TomeSpell) && canUseAction(node.getInventory(), action.getIngredientCost()));
    }

    // TODO - If NODE contains a potion or spell learning (favs) in the current parent actions of the node and it has met the wanted min potion price
    private static boolean containsDesiredAction(Node node, Potion targetedPotion) {
        for (Action action : node.getParentActions()) {
            if (action.getId() == targetedPotion.getId()) {
                return true;
            }
        }

        return false;
    }

    // TODO - Node is eligible for search if it does not contain in the current list a potion and does not over extend limited deep search nodes
    private static boolean isNodeEligibleForSearch(Node node) {
        for (Action action : node.getParentActions()) {
            if (action instanceof Potion) {
                return false;
            }
        }

        return node.getParentActions().size() <= ZAI_DEEP_SEARCH_NODES;
    }

    private static List<Integer> calculateNewInventoryState(int[] inventory, int[] cost, int repeat) {
        List<Integer> newIngredients = new ArrayList<>();
        int spaceAfterCommandUse = 0;

        if (repeat <= 1) {
            for (int i = 0; i < 4; i++) {
                newIngredients.add(inventory[i] + cost[i]);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                int sum = inventory[i] + (cost[i] * repeat);
                spaceAfterCommandUse += sum;

                if (sum < 0) {
                    return null;
                }

                newIngredients.add(sum);
            }

            if (spaceAfterCommandUse > 10) {
                return null;
            }
        }

        return newIngredients;
    }

    interface Action {

        String getAction();

        int getId();

        int[] getIngredientCost();

        int getPrice();

        int getTomeIndex();

        int getTaxCount();

        int getCastable();

        int getRepeatable();

        boolean isActionCastable();

        Action cloneAsUsed();

        Action cloneAsCastable();

        int getNumberOfRepeatableUsedInSimulation();
    }

    static class Node implements Comparable<Node> {

        private final int[] inventory;
        private final List<Action> parentActions;
        private final List<Action> availableActions;
        private final double value;

        // TODO - Starting node
        public Node(int[] inventory, List<Action> availableActions, double value) {
            this.inventory = inventory;
            this.parentActions = new ArrayList<>();
            this.availableActions = availableActions;
            this.value = value;
        }

        // TODO - Every new node from start point
        public Node(int[] inventory, List<Action> parentActions, List<Action> availableActions, double value) {
            this.inventory = inventory;
            this.parentActions = parentActions;
            this.availableActions = availableActions;
            this.value = value;
        }

        public List<Action> getParentActions() {
            return parentActions;
        }

        public int[] getInventory() {
            return inventory;
        }

        @Override
        public Node clone() {
            int[] newInventory = inventory.clone();
            List<Action> newParentActions = new ArrayList<>(parentActions);
            List<Action> newAvailableActions = new ArrayList<>(availableActions);
            return new Node(newInventory, newParentActions, newAvailableActions, value);
        }

        @Override
        public int compareTo(Node other) {
            if (value < other.getValue()) {
                return -1;
            } else if (value > other.getValue()) {
                return 1;
            } else {
                int thisSum = IntStream.of(inventory).sum();
                int otherSum = IntStream.of(other.getInventory()).sum();

                if (thisSum > otherSum) {
                    return -1;
                } else if (otherSum > thisSum) {
                    return 1;
                }

                return 0;
            }
        }

        // TODO - Add new Node to the Graph - first calculate new inv state and new available actions state depending on the action wanted to be used
        public Node addNewNode(Action action, int repeatable) {
            int[] newInv = calculateNewInventoryStateDependingOnActionType(action, repeatable);

            if (newInv == null) {
                return null;
            }

            List<Action> newAvailableActions = cloneAvailableActionsDependingOnActionType(action);

            // TODO Test? Shallow copy ?
            List<Action> newParent = new ArrayList<>(parentActions);

            if (action instanceof Spell) {
                action = new Spell(
                        action.getId(), action.getIngredientCost(), action.getPrice(), action.getTomeIndex(), action.getTaxCount(), action.getCastable(), action.getRepeatable(), repeatable);
            }

            newParent.add(action);

            // TODO Natural Order of the Priority Queue, so we want best Node to be smallest as possible

            int newNodeValue = newParent.size();
            newNodeValue -= repeatable * 2;
            newNodeValue -= action instanceof Rest ? 2 : 0;

            // TODO Add additional negative value if we can brew a potion
            if (action instanceof Potion) {
                if ((POTIONS_SOLD_ME >= ZAI_FINISH_ASAP_IF_I_HAVE_POTIONS) || (POTION_SOLD_ENEMY >= ZAI_FINISH_ASAP_IF_ENEMY_HAS_POTIONS)) {
                    switch (newParent.size()) {
                        case 1:
                            newNodeValue -= 10 + (action.getPrice() / ZAI_PRICE_SENSITIVITY_PATH_DECIDE);
                        case 2:
                            newNodeValue -= 8 + (action.getPrice() / ZAI_PRICE_SENSITIVITY_PATH_DECIDE);
                        case 3:
                            newNodeValue -= 6 + (action.getPrice() / ZAI_PRICE_SENSITIVITY_PATH_DECIDE);
                        case 4:
                            newNodeValue -= 4 + (action.getPrice() / ZAI_PRICE_SENSITIVITY_PATH_DECIDE);
                        case 5:
                            newNodeValue -= 2 + (action.getPrice() / ZAI_PRICE_SENSITIVITY_PATH_DECIDE);
                        default:
                            newNodeValue -= 1 + (action.getPrice() / ZAI_PRICE_SENSITIVITY_PATH_DECIDE);
                    }
                } else {
                    newNodeValue -= 10 + (action.getPrice() / ZAI_PRICE_SENSITIVITY_PATH_DECIDE);
                    newNodeValue += action.getPrice() < 10 ? 5 : 0;
                }
            }

            return new Node(newInv, newParent, newAvailableActions, newNodeValue);
        }

        private List<Action> cloneAvailableActionsDependingOnActionType(Action action) {
            List<Action> newAvailableActions = new ArrayList<>();

            // TODO - If action is REST refresh all existing actions
            if (action instanceof Rest) {
                for (Action cloneAction : availableActions) {
                    newAvailableActions.add(cloneAction.cloneAsCastable());
                }

                return newAvailableActions;
            }

            // TODO - If learning spell used, do not add new learning spell but new action to be used
            if (action instanceof TomeSpell) {
                for (Action cloneAction : availableActions) {
                    if (cloneAction.equals(action)) {
                        // TODO - Remmevber that LEARN iD is different from CAST ID at the end !!! Achtung
                        newAvailableActions
                                .add(new Spell(action.getId(), action.getIngredientCost(),
                                        action.getPrice(), action.getTomeIndex(),
                                        action.getTaxCount(), action.getCastable(), action.getRepeatable(),
                                        0));
                    } else {
                        newAvailableActions.add(cloneAction);
                    }
                }

                return newAvailableActions;
            }

            // TODO - Otherwise copy all actions, but mark the one used as castable = 0
            for (Action cloneAction : availableActions) {
                if (cloneAction.equals(action)) {
                    newAvailableActions.add(cloneAction.cloneAsUsed());
                } else {
                    newAvailableActions.add(cloneAction);
                }
            }

            return newAvailableActions;
        }

        // TODO - calculate new inventory state depending on the spell usage
        private int[] calculateNewInventoryStateDependingOnActionType(Action action, int repeat) {
            if (action instanceof TomeSpell) {
                return calculateNewInvStateForTomePurchase(inventory, action);
            }

            return calculateNewInvState(inventory, action, repeat);
        }

        // TODO - If action is of any type outside Learning then calculate the new inventory state by taking my inventory +/- spell usage
        private int[] calculateNewInvState(int[] inventory, Action spell, int repeat) {
            int[] newIngredients = new int[4];
            int spaceAfterCommandUse = 0;

            if (repeat <= 1) {
                for (int i = 0; i < 4; i++) {
                    int sum = inventory[i] + spell.getIngredientCost()[i];
                    spaceAfterCommandUse += sum;
                    newIngredients[i] = sum;

                    if (sum < 0) {
                        return null;
                    }
                }
            } else {
                for (int i = 0; i < 4; i++) {
                    int sum = inventory[i] + (spell.getIngredientCost()[i] * repeat);
                    spaceAfterCommandUse += sum;

                    if (sum < 0) {
                        return null;
                    }

                    newIngredients[i] = sum;
                }
            }

            if (spaceAfterCommandUse > 10) {
                return null;
            }

            return newIngredients;
        }

        // TODO - If action is of type TomeSpell then i have to see see how much will i pay in the blues (Tier 0) depending on the tome index
        private int[] calculateNewInvStateForTomePurchase(int[] inventory, Action learnSpell) {
            int[] newIngredients = new int[4];
            int spaceAfterCommandUse = 0;

            for (int i = 0; i < 4; i++) {
                if (i == 0) {
                    int sum = inventory[i] - learnSpell.getTomeIndex() + learnSpell.getTaxCount();
                    newIngredients[i] = sum;
                    spaceAfterCommandUse += sum;

                    if (sum < 0) {
                        return null;
                    }
                } else {
                    newIngredients[i] = inventory[i];
                    spaceAfterCommandUse += inventory[i];
                }
            }

            if (spaceAfterCommandUse > 10) {
                return null;
            }

            return newIngredients;
        }

        public double getValue() {
            return value;
        }

        @Override
        public String toString() {
            String commandChain = "[";

            for (Action action : parentActions) {
                commandChain += action.getAction() + ", ";
            }

            commandChain += "]";

            return "Value: " + getValue() + " - " + commandChain.toString();
        }

        public Action getFirstAction() {
            if (parentActions == null || parentActions.isEmpty()) {
                return ACTION_REST;
            }

            return parentActions.get(0);
        }

        public boolean isActionCastable(Action action) {
            return action.isActionCastable();
        }

        public int howManyTimeIsActionRepeatable(Action action) {
            return action.getRepeatable();
        }

        public List<Action> getAvailableActions() {
            return availableActions;
        }
    }

    static class Rest implements Action {

        public Rest() {
        }

        @Override
        public String getAction() {
            return "REST";
        }

        @Override
        public int getId() {
            return 0;
        }

        @Override
        public int[] getIngredientCost() {
            return NO_INGREDIENT_COST;
        }

        @Override
        public int getPrice() {
            return 0;
        }

        @Override
        public int getTomeIndex() {
            return 0;
        }

        @Override
        public int getTaxCount() {
            return 0;
        }

        @Override
        public int getCastable() {
            return 0;
        }

        @Override
        public int getRepeatable() {
            return 0;
        }

        @Override
        public boolean isActionCastable() {
            return true;
        }

        @Override
        public Action cloneAsUsed() {
            return ACTION_REST;
        }

        @Override
        public Action cloneAsCastable() {
            return ACTION_REST;
        }

        @Override
        public int getNumberOfRepeatableUsedInSimulation() {
            return 0;
        }

        @Override
        public String toString() {
            return "REST";
        }
    }

    static class Spell implements Action {

        private final int id;
        private final int[] ingredientCost;
        private final int price;
        private final int tomeIndex;
        private final int taxCount;
        private final int castable;
        private final int repeatable;
        private final int numberOfRepeatableUsedInSimulation;

        public Spell(int id, int[] ingredientCost, int price, int tomeIndex, int taxCount, int castable, int repeatable, int numberOfRepeatableUsedInSimulation) {
            this.id = id;
            this.ingredientCost = ingredientCost;
            this.price = price;
            this.tomeIndex = tomeIndex;
            this.taxCount = taxCount;
            this.castable = castable;
            this.repeatable = repeatable;
            this.numberOfRepeatableUsedInSimulation = numberOfRepeatableUsedInSimulation;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public int[] getIngredientCost() {
            return ingredientCost;
        }

        @Override
        public int getPrice() {
            return price;
        }

        @Override
        public int getTomeIndex() {
            return tomeIndex;
        }

        @Override
        public int getTaxCount() {
            return taxCount;
        }

        @Override
        public int getCastable() {
            return castable;
        }

        @Override
        public int getRepeatable() {
            return repeatable;
        }

        @Override
        public String getAction() {
            if (numberOfRepeatableUsedInSimulation != 0) {
                return "CAST " + getId() + " " + getNumberOfRepeatableUsedInSimulation();
            }

            return "CAST " + getId();

        }

        @Override
        public boolean isActionCastable() {
            return getCastable() != 0;
        }

        @Override
        public Action cloneAsUsed() {
            return new Spell(id, ingredientCost, price, tomeIndex, taxCount, 0, repeatable, numberOfRepeatableUsedInSimulation);
        }

        @Override
        public Action cloneAsCastable() {
            return new Spell(id, ingredientCost, price, tomeIndex, taxCount, 1, repeatable, numberOfRepeatableUsedInSimulation);
        }

        @Override
        public int getNumberOfRepeatableUsedInSimulation() {
            return numberOfRepeatableUsedInSimulation;
        }

        @Override
        public String toString() {
            return "Spell{" +
                    "id=" + id +
                    ", ingredientCost=" + ingredientCost +
                    ", price=" + price +
                    ", tomeIndex=" + tomeIndex +
                    ", taxCount=" + taxCount +
                    ", castable=" + castable +
                    ", repeatable=" + repeatable +
                    '}';
        }
    }

    static class Potion implements Action {

        private final int id;
        private final int[] ingredientCost;
        private final int price;
        private final int tomeIndex;
        private final int taxCount;
        private final int castable;
        private final int repeatable;

        public Potion(int id, int[] ingredientCost, int price, int tomeIndex, int taxCount, int castable, int repeatable) {
            this.id = id;
            this.ingredientCost = ingredientCost;
            this.price = price;
            this.tomeIndex = tomeIndex;
            this.taxCount = taxCount;
            this.castable = castable;
            this.repeatable = repeatable;
        }

        @Override
        public int getTomeIndex() {
            return tomeIndex;
        }

        @Override
        public int getTaxCount() {
            return taxCount;
        }

        @Override
        public int getCastable() {
            return castable;
        }

        @Override
        public int getRepeatable() {
            return repeatable;
        }

        @Override
        public String getAction() {
            return "BREW " + getId();
        }

        @Override
        public boolean isActionCastable() {
            return true;
        }

        @Override
        public Action cloneAsUsed() {
            return this;
        }

        @Override
        public Action cloneAsCastable() {
            return this;
        }

        @Override
        public int getNumberOfRepeatableUsedInSimulation() {
            return 0;
        }

        public int getId() {
            return id;
        }

        public int[] getIngredientCost() {
            return ingredientCost;
        }

        public int getPrice() {
            return price;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Potion potion = (Potion) o;
            return id == potion.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "id=" + id + " $=" + price + " " + ingredientCost;
        }
    }

    static class TomeSpell implements Action {

        private final int id;
        private final int[] ingredientCost;
        private final int price;
        private final int tomeIndex;
        private final int taxCount;
        private final int castable;
        private final int repeatable;

        public TomeSpell(int id, int[] ingredientCost, int price, int tomeIndex, int taxCount, int castable, int repeatable) {
            this.id = id;
            this.ingredientCost = ingredientCost;
            this.price = price;
            this.tomeIndex = tomeIndex;
            this.taxCount = taxCount;
            this.castable = castable;
            this.repeatable = repeatable;
        }

        int calculatedValueOfTomeSpell() {
            int valueGain = 0;
            int valueCost = 0;

            for (int i = 0; i < 4; i++) {
                int ingredient = ingredientCost[i];

                if (ingredient >= 0) {
                    valueGain += ingredient * (i + 1);
                } else {
                    valueCost += ingredient * (i + 1);
                }
            }

            if (valueCost == 0) {
                return valueGain + taxCount + repeatable;
            }

            return valueGain + valueCost + 2 * (taxCount - tomeIndex) + repeatable;
        }

        public boolean isAllPositive() {
            for (int i = 0; i < 4; i++) {
                if (ingredientCost[i] < 0) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public int[] getIngredientCost() {
            return ingredientCost;
        }

        public int getPrice() {
            return price;
        }

        @Override
        public int getTomeIndex() {
            return tomeIndex;
        }

        @Override
        public int getTaxCount() {
            return taxCount;
        }

        @Override
        public int getCastable() {
            return castable;
        }

        @Override
        public int getRepeatable() {
            return repeatable;
        }

        @Override
        public String getAction() {
            return "LEARN " + getId();
        }

        @Override
        public boolean isActionCastable() {
            return true;
        }

        @Override
        public Action cloneAsUsed() {
            return this;
        }

        @Override
        public Action cloneAsCastable() {
            return this;
        }

        @Override
        public int getNumberOfRepeatableUsedInSimulation() {
            return 0;
        }

        @Override
        public String toString() {
            return "id=" + id +
                    ", " + ingredientCost +
                    ", price=" + price +
                    ", repeatable=" + repeatable +
                    ", calculatedValue=" + calculatedValueOfTomeSpell() +
                    '}';
        }
    }

    static class Inventory {

        private final int[] ingredients;
        private final int score;

        public Inventory(int[] ingredients, int score) {
            this.ingredients = ingredients;
            this.score = score;
        }

        public int[] getIngredients() {
            return ingredients;
        }

        public int getScore() {
            return score;
        }

        @Override
        public Inventory clone() {
            int[] newIngredients = ingredients.clone();
            return new Inventory(newIngredients, score);
        }

        @Override
        public String toString() {
            return "INV " + ingredients;
        }
    }
}