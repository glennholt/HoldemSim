package com.company;

import java.util.*;
import java.util.regex.*;
import java.io.*;

import static java.util.Collections.swap;
import static java.util.Collections.shuffle;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.apache.commons.collections4.ListUtils;

class CommandProcessor {
    String commandRegex = "\\s*(?:([ip])(\\d+))|(?:([HFTR])([AKQJT987654321][SHCD]))";
    Scanner scanner;
    MatchResult matchResult;

    ArrayList<String> holeCards, flopCards, turnCard, riverCard;
    int numPlayers, numIterations;

    public CommandProcessor() {
        numPlayers = numIterations = 0;

        holeCards = new ArrayList<String>(2);
        flopCards = new ArrayList<String>(3);
        turnCard  = new ArrayList<String>(1);
        riverCard = new ArrayList<String>(1);
    }

    public void process(String theCommand) {
        scanner = new Scanner(theCommand);
        while (scanner.findInLine(commandRegex) != null) {
            matchResult = scanner.match();

            if (matchResult.group(1) != null) {
                switch (matchResult.group(1).charAt(0)) {
                    case 'p': numPlayers = Integer.valueOf(matchResult.group(2)); break;
                    case 'i': numIterations = Integer.valueOf(matchResult.group(2)); break;
                }
            } else if (matchResult.group(3) != null) {
                switch (matchResult.group(3).charAt(0)) {
                    case 'H': holeCards.add(matchResult.group(4)); break;
                    case 'F': flopCards.add(matchResult.group(4)); break;
                    case 'T': turnCard.add(matchResult.group(4)); break;
                    case 'R': riverCard.add(matchResult.group(4)); break;
                }
            }
        }
    }
}

class Card {
    static char[] Suits = {'S', 'H', 'C', 'D'} ;
    static char[] Ranks = {'A', 'K', 'Q', 'J', 'T', '9', '8', '7', '6', '5', '4', '3', '2'};

    int ID;

    public Card(int _ID) { ID = _ID; }

    public int getSuit() { return ((ID - 1) / 13); }
    public int getRank() { return ((ID - 1) % 13); }

    public String toString() {  return Character.toString(Ranks[ getRank() ]) + Suits[ getSuit() ] ; }
}

class HandValue implements Serializable {
    Deck.HandRanks handRank;
    int maxRank;
    int kicker;

    public HandValue(Deck.HandRanks _handRank, int _maxRank, int _kicker) {
        handRank = _handRank;
        maxRank = _maxRank;
        kicker = _kicker;
    }

    public String toString() {
        return handRank.toString() + ":" + maxRank + ":" + kicker;
    }
}

class Deck {
    public enum HandRanks { HIGH_CARD, PAIR, TWO_PAIR, THREE_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_KIND, STRAIGHT_FLUSH }

    Random randGen = new Random();
    ArrayList<Card> cards;
    ArrayList<Card> usedCards;
    ArrayList<Card> reservedCards;
    Hashtable<String, Card> cardsByName;

    public Deck() {
        cards = new ArrayList<Card>(52);
        usedCards = new ArrayList<Card>(52);
        reservedCards = new ArrayList<Card>(52);
        cardsByName = new Hashtable<String, Card>(52);

        Card card;
        for (int i = 1; i <= 52; i++) {
            card = new Card(i);
            cards.add(card);
            cardsByName.put(card.toString(), card);
        }
    }

    public static void genLookupTable() {
        Iterator<int[]> i = CombinatoricsUtils.combinationsIterator(52,5);
        Hashtable<String, Long> lookupTable = new Hashtable<String, Long>(3200000);
        Card[] chosenCards = new Card[5];

        while (i.hasNext()) {
            int counter = 0;
            for (int j : i.next()) {
                chosenCards[counter++] = new Card(j + 1);
            }
        }

        try {
            FileOutputStream file = new FileOutputStream("holdemTable.dat");
            ObjectOutputStream out = new ObjectOutputStream(file);

            out.writeObject(lookupTable);

            out.close();
            file.close();
        }
        catch (IOException ex) { System.out.println("IOException is caught: " + ex.toString()); }

    }

    private static Long analyze5CardHand(Card[] chosenCards) {
        int[] rankCount = new int[13];
        long straight = 0, flush = 0;
        int totalRank = 0, maxRank = 99, kicker = 99, currRank = 0;

        for (Card card : chosenCards) {
            int rank = card.getRank();

            straight |= (1 << rank);
            rankCount[rank]++;

            flush |= ( 1 << card.getSuit());

            if (rank < maxRank) { maxRank = rank; }
        }

        while (straight % 2 == 0) { straight >>= 1; }

        boolean hasStraight = ( straight == 0b11111 || straight == 0b1111000000001 );
        boolean hasFlush = (flush & (flush - 1)) == 0;

        if (straight == 0b1111000000001) { maxRank = 9; }
        if (!hasStraight && !hasFlush) { maxRank = 99; }

        long bitKicker = 0;
        HandValue bestHand;

        for (int count : rankCount) {
            if (count == 4) { bestHand = new HandValue(HandRanks.FOUR_KIND, currRank, 99); }
            if (count == 3) {
                if (totalRank == 2) { kicker = maxRank; }

                maxRank = currRank;
                totalRank += 3;
            }
            else if (count == 2) {
                switch (totalRank) {
                    case 3:
                    case 2: kicker = currRank; break;
                    default:
                        if (currRank < maxRank) { maxRank = currRank; }
                        break;
                }

                totalRank += 2;
            }
            else if (count == 1) { bitKicker |= (1 << (13 - currRank)); }

            currRank++;
        }

        if (hasStraight && hasFlush) { bestHand = new HandValue(HandRanks.STRAIGHT_FLUSH, maxRank, kicker); }
        else if (hasFlush) { bestHand = new HandValue(HandRanks.FLUSH, maxRank, kicker); }
        else if (hasStraight) { bestHand = new HandValue(HandRanks.STRAIGHT, maxRank, kicker); }
        else if (totalRank == 5) { bestHand = new HandValue(HandRanks.FULL_HOUSE, maxRank, kicker); }
        else if (totalRank == 3) { bestHand = new HandValue(HandRanks.THREE_KIND, maxRank, kicker); }
        else if (totalRank == 4) { bestHand = new HandValue(HandRanks.TWO_PAIR, maxRank, kicker); }
        else if (totalRank == 2) { bestHand = new HandValue(HandRanks.PAIR, maxRank, kicker); }
        else { bestHand = new HandValue(HandRanks.HIGH_CARD, maxRank, kicker); }

        if (kicker != 99) { bitKicker |= (1 << (26 - kicker)); }
        bitKicker |= (1L << (39 - maxRank));
        bitKicker |= (1L << (40 + bestHand.handRank.ordinal()));
        if (straight == 0b1111000000001) {
            bitKicker &= 0b1111111111111111111111111111111111111111111111111101111111111111L;
            bitKicker++;
        }

        return new Long(bitKicker);
    }

    public Card getCardByName (String name) {
        return cardsByName.get(name);
    }

    public Card deal(Card card) {
        cards.remove(card);
        usedCards.add(card);

        return card;
    }

    public Card deal() {
        Card dealt = cards.remove(0);

        usedCards.add(dealt);
        return dealt;
    }

    public void shuffle() {
        // Add back any used cards
        cards.addAll(usedCards);
        usedCards.clear();

        java.util.Collections.shuffle(cards, randGen);
    }

    public String toString() {
        return cards.toString();
    }
}

class Player {
    enum PlayerState { Active, Fold, SittingOut };

    PlayerState state;
    String name;

    ArrayList<Card> holeCards;

    public Player(String _name) {
        name = _name;
        holeCards = new ArrayList<Card>(2);
        state = PlayerState.Active;
    }

    public String toString() {
        return name + ": " + holeCards.toString() + " " + state;
    }
}

class Table {
    public enum PokerRound { PRE_FLOP, FLOP, TURN, RIVER };

    static Hashtable<String, Long> lookupTable;
    Deck theCards;
    int numCardsDealt;
    ArrayList<Card> boardCards;
    ArrayList<Player> players;

    public Table(int numPlayers) {
        theCards = new Deck();
        players = new ArrayList<Player> (numPlayers);
        boardCards = new ArrayList<Card> (5);
        numCardsDealt = 0;

        for (int i = 0; i < numPlayers; i++) {
            players.add(new Player("Player " + String.valueOf(i + 1)));
        }
    }

    static public Hashtable<String, Long> readLookupTable(String filename) {
        lookupTable = new Hashtable<String, Long>(3200000);

        try {
            FileInputStream file = new FileInputStream(filename);
            ObjectInputStream in = new ObjectInputStream(file);

            lookupTable = (Hashtable<String, Long>)in.readObject();

            in.close();
            file.close();
        }

        catch(IOException ex)
        {
            System.out.println("IOException is caught: " + ex.toString());
        }

        catch(ClassNotFoundException ex)
        {
            System.out.println("ClassNotFoundException is caught");
        }

        return lookupTable;
    }

    public void setReservedCards(ArrayList<String> reservedCards) {
        Card card;

        for (String cardName : reservedCards) {
            card = theCards.getCardByName(cardName);

            theCards.reservedCards.add(card);
            theCards.cards.remove(card);
        }
    }

    public void dealCards(Table.PokerRound round, ArrayList<String> staticCards) {
        switch (round) {
            case PRE_FLOP:
                for (int j = 0; j < players.size(); j++) {
                    if (j == 0 && numCardsDealt <  staticCards.size()) {
                        players.get(j).holeCards.add(theCards.getCardByName(staticCards.get(numCardsDealt)));
                    }
                    else {
                        players.get(j).holeCards.add(theCards.deal());
                    }
                }
                break;
            case FLOP:
                if (numCardsDealt - 2 < staticCards.size()) {
                    boardCards.add(theCards.getCardByName(staticCards.get(numCardsDealt - 2)));
                }
                else { boardCards.add(theCards.deal()); }
                break;
            case TURN:
            case RIVER:
                if (staticCards.size() > 0) {
                    boardCards.add(theCards.getCardByName(staticCards.get(0)));
                }
                else { boardCards.add(theCards.deal()); }
                break;
        }

        numCardsDealt++;
    }

    public boolean findWinner() {
        ArrayList<Card> fullHand = new ArrayList<Card>(7);
        Card[] chosenCards = new Card[5], bestCards = new Card[5];
        ArrayList<Player> winnerList = new ArrayList<Player>(5);
        long winnerScore = 0;

        for (Player player : players) {
            fullHand.clear();
            fullHand.addAll(boardCards);
            fullHand.addAll(player.holeCards);
            fullHand.sort(new Comparator<Card> () {
                public int compare(Card obj1, Card obj2) {
                    return (obj1.ID > obj2.ID ? 1 : -1);
                }} );

            Iterator<int[]> i = CombinatoricsUtils.combinationsIterator(7,5);
            long currentHand, bestHand = 0;

            while (i.hasNext()) {
                int counter = 0;

                for (int j : i.next()) {
                    chosenCards[counter++] = new Card(fullHand.get(j).ID);
                }

                currentHand = lookupTable.get(Arrays.toString(chosenCards));

                if (currentHand > bestHand) {
                    bestHand = currentHand;
                    bestCards = chosenCards.clone();
                }
            }

            if (bestHand > winnerScore) {
                winnerScore = bestHand;

                winnerList.clear();
                winnerList.add(player);
            }
            else if (bestHand == winnerScore) { winnerList.add(player); }
        }

        int highRank = (int) ((winnerScore & 0b0000000000000001111111110000000000000000000000000000000000000000L) >> 40);
        return (winnerList.contains(players.get(0)));
        //return Deck.HandRanks.values()[Long.numberOfTrailingZeros(highRank)];
    }

    public String toString() {
        return boardCards.toString() + "\n" + players.toString();
    }
}

public class Main {
    public static void main(String[] args) {
        Table.readLookupTable("holdemTable.dat");
        //Deck.genLookupTable();

        while (true) {
            CommandProcessor commandProcessor = new CommandProcessor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String commands = null;

            System.out.print("Enter command --> ");
            try {
                commands = reader.readLine();
            } catch (IOException e) { e.printStackTrace(); }

            commandProcessor.process(commands);

            Hashtable<Deck.HandRanks, Integer> results = new Hashtable<Deck.HandRanks, Integer>(10);
            for (Deck.HandRanks handRank : Deck.HandRanks.values()) {
                results.put(handRank, 0);
            }

            int numWins = 0;
            Table pokerTable = new Table(commandProcessor.numPlayers);

            pokerTable.setReservedCards((ArrayList<String>) ListUtils.union(ListUtils.union(ListUtils.union(commandProcessor.holeCards,
                    commandProcessor.flopCards), commandProcessor.turnCard), commandProcessor.riverCard));

            for (int i = 0; i < commandProcessor.numIterations; i++) {
                pokerTable.theCards.shuffle();

                pokerTable.dealCards(Table.PokerRound.PRE_FLOP, commandProcessor.holeCards);
                pokerTable.dealCards(Table.PokerRound.PRE_FLOP, commandProcessor.holeCards);
                pokerTable.dealCards(Table.PokerRound.FLOP, commandProcessor.flopCards);
                pokerTable.dealCards(Table.PokerRound.FLOP, commandProcessor.flopCards);
                pokerTable.dealCards(Table.PokerRound.FLOP, commandProcessor.flopCards);
                pokerTable.dealCards(Table.PokerRound.TURN, commandProcessor.turnCard);
                pokerTable.dealCards(Table.PokerRound.RIVER, commandProcessor.riverCard);

                if (pokerTable.findWinner()) { numWins++; }

                //results.put(winningRank, new Integer(results.get(winningRank) + 1));

                // Clean Up
                pokerTable.numCardsDealt = 0;
                pokerTable.boardCards.clear();
                for (Player player : pokerTable.players) {
                    player.holeCards.clear();
                }
            }

            System.out.println("Wins = " + numWins);
        }
    }
}
