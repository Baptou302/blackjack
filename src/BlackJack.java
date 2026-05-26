import java.awt.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;

public class BlackJack {

    // ── Valeurs des jetons ──────────────────────────────────────────────
    private static final int JETON_BLEU  = 10;
    private static final int JETON_ROSE  = 25;
    private static final int JETON_ROUGE = 50;

    private class Card {
        String value, type;
        Card(String value, String type) { this.value = value; this.type = type; }
        public String toString()  { return value + "-" + type; }
        public int getValue() {
            if ("AJQK".contains(value)) return value.equals("A") ? 11 : 10;
            return Integer.parseInt(value);
        }
        public boolean isAce()        { return value.equals("A"); }
        public String getImagePath()  { return "asset/cards/" + toString() + ".png"; }
    }

    ArrayList<Card> deck;
    Random random = new Random();

    // dealer
    Card hiddenCard;
    ArrayList<Card> dealerHand;
    int dealerSum, dealerAceCount;

    // player
    ArrayList<Card> playerHand;
    int playerSum, playerAceCount;

    // economy
    int solde      = 500;   // argent disponible
    int mise       = 0;     // mise en cours
    boolean partieFinie = false;

    // dimensions
    int boardWidth  = 680;
    int boardHeight = 600;
    int cardWidth   = 110;
    int cardHeight  = 154;

    // ── Composants principaux ───────────────────────────────────────────
    JFrame frame = new JFrame("Black Jack");

    JPanel gamePanel = new JPanel() {
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            try {
                // background
                Image bg = new ImageIcon("asset/add/background.png").getImage();
                g.drawImage(bg, 0, 0, getWidth(), getHeight(), null);

                // carte cachée du croupier
                Image hiddenImg = new ImageIcon("asset/cards/BACK.png").getImage();
                if (!stayButton.isEnabled()) {
                    hiddenImg = new ImageIcon(hiddenCard.getImagePath()).getImage();
                }
                g.drawImage(hiddenImg, 20, 20, cardWidth, cardHeight, null);

                // cartes du croupier
                for (int i = 0; i < dealerHand.size(); i++) {
                    Image img = new ImageIcon(dealerHand.get(i).getImagePath()).getImage();
                    g.drawImage(img, cardWidth + 25 + (cardWidth + 5) * i, 20, cardWidth, cardHeight, null);
                }

                // cartes du joueur
                for (int i = 0; i < playerHand.size(); i++) {
                    Image img = new ImageIcon(playerHand.get(i).getImagePath()).getImage();
                    g.drawImage(img, 20 + (cardWidth + 5) * i, 320, cardWidth, cardHeight, null);
                }

                // résultat quand la partie est terminée
                if (!stayButton.isEnabled()) {
                    dealerSum = reduceDealerAce();
                    playerSum = reducePlayerAce();

                    String message;
                    int gain = 0;
                    // La mise a déjà été déduite du solde dans lancerPartie().
                    // Victoire  → on rend la mise + le gain (mise * 2)
                    // Égalité   → on rend juste la mise (mise * 1)
                    // Défaite   → rien à ajouter (gain = 0)
                    if (playerSum > 21) {
                        message = "Tu as perdu !";
                        gain = 0;           // mise déjà perdue
                    } else if (dealerSum > 21) {
                        message = "C'est gagné !";
                        gain = mise * 2;    // mise remboursée + profit
                    } else if (playerSum == dealerSum) {
                        message = "Égalité !";
                        gain = mise;        // mise remboursée seulement
                    } else if (playerSum > dealerSum) {
                        message = "C'est gagné !";
                        gain = mise * 2;    // mise remboursée + profit
                    } else {
                        message = "Tu as perdu !";
                        gain = 0;           // mise déjà perdue
                    }

                    if (!partieFinie) {
                        solde += gain;
                        partieFinie = true;
                        updateBettingUI();
                    }

                    g.setFont(new Font("Georgia", Font.BOLD, 34));
                    g.setColor(new Color(255, 215, 0));
                    g.drawString(message, 200, 265);
                }

            } catch (Exception e) { e.printStackTrace(); }
        }
    };

    JPanel buttonPanel = new JPanel();
    JButton hitButton     = new JButton("Une carte !");
    JButton stayButton    = new JButton("Je reste !");
    JButton restartButton = new JButton("Nouvelle partie");

    // ── Fenêtre de mise (JDialog modale) ────────────────────────────────
    JDialog bettingDialog;
    JLabel  soldeLabelDialog;
    JLabel  miseLabelDialog;
    JButton btnBleu, btnRose, btnRouge;
    JButton btnAnnuler, btnValider;
    JLabel  imgBleu, imgRose, imgRouge;

    // ── Barre d'info en haut du jeu ──────────────────────────────────────
    JLabel infoLabel;

    // ════════════════════════════════════════════════════════════════════
    BlackJack() {
        buildBettingDialog();
        bettingDialog.setVisible(true); // on commence par miser

        frame.setVisible(true);
        frame.setSize(boardWidth, boardHeight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        gamePanel.setLayout(new BorderLayout());
        gamePanel.setBackground(new Color(34, 85, 54));
        frame.add(gamePanel);

        // barre infos solde / mise
        infoLabel = new JLabel();
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoLabel.setFont(new Font("Georgia", Font.BOLD, 15));
        infoLabel.setForeground(new Color(255, 215, 0));
        infoLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        infoLabel.setOpaque(true);
        infoLabel.setBackground(new Color(20, 60, 35));
        frame.add(infoLabel, BorderLayout.NORTH);
        refreshInfoLabel();

        // boutons
        styleButton(hitButton,     new Color(46, 125, 50),  Color.WHITE);
        styleButton(stayButton,    new Color(183, 28, 28),  Color.WHITE);
        styleButton(restartButton, new Color(33, 33, 33),   Color.WHITE);

        buttonPanel.setBackground(new Color(20, 60, 35));
        buttonPanel.add(hitButton);
        buttonPanel.add(stayButton);
        buttonPanel.add(restartButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        // ── Listeners ──────────────────────────────────────────────────
        hitButton.addActionListener(e -> {
            Card card = deck.remove(deck.size() - 1);
            playerSum  += card.getValue();
            playerAceCount += card.isAce() ? 1 : 0;
            playerHand.add(card);
            if (reducePlayerAce() > 21) hitButton.setEnabled(false);
            gamePanel.repaint();
        });

        stayButton.addActionListener(e -> {
            hitButton.setEnabled(false);
            stayButton.setEnabled(false);
            while (dealerSum < 17) {
                Card card = deck.remove(deck.size() - 1);
                dealerSum += card.getValue();
                dealerAceCount += card.isAce() ? 1 : 0;
                dealerHand.add(card);
            }
            gamePanel.repaint();
        });

        restartButton.addActionListener(e -> {
            // ouvre la fenêtre de mise avant chaque nouvelle partie
            bettingDialog.setVisible(true);
        });

        gamePanel.repaint();
    }

    // ── Fenêtre de mise ─────────────────────────────────────────────────
    private void buildBettingDialog() {
        bettingDialog = new JDialog(frame, "Placer votre mise", true);
        bettingDialog.setSize(420, 370);
        bettingDialog.setLocationRelativeTo(frame);
        bettingDialog.setResizable(false);
        bettingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(new Color(20, 60, 35));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // titre
        JLabel titre = new JLabel("🎰  Placer votre mise", SwingConstants.CENTER);
        titre.setFont(new Font("Georgia", Font.BOLD, 22));
        titre.setForeground(new Color(255, 215, 0));
        root.add(titre, BorderLayout.NORTH);

        // centre : solde + jetons
        JPanel centre = new JPanel(new GridBagLayout());
        centre.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);

        soldeLabelDialog = new JLabel("Solde : " + solde + " €");
        soldeLabelDialog.setFont(new Font("Georgia", Font.PLAIN, 15));
        soldeLabelDialog.setForeground(Color.WHITE);
        miseLabelDialog  = new JLabel("Mise actuelle : 0 €");
        miseLabelDialog.setFont(new Font("Georgia", Font.BOLD, 15));
        miseLabelDialog.setForeground(new Color(255, 215, 0));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        centre.add(soldeLabelDialog, gbc);
        gbc.gridy = 1;
        centre.add(miseLabelDialog, gbc);

        // jetons
        gbc.gridwidth = 1;
        gbc.gridy = 2;

        imgBleu  = loadJetonLabel("asset/jetons_bleu.png",  JETON_BLEU  + " €");
        imgRose  = loadJetonLabel("asset/jetons_rose.png",  JETON_ROSE  + " €");
        imgRouge = loadJetonLabel("asset/jetons_rouge.png", JETON_ROUGE + " €");

        btnBleu  = jetonButton(JETON_BLEU,  new Color(30, 100, 200));
        btnRose  = jetonButton(JETON_ROSE,  new Color(220, 80, 140));
        btnRouge = jetonButton(JETON_ROUGE, new Color(200, 30, 30));

        gbc.gridx = 0; centre.add(imgBleu,  gbc);
        gbc.gridx = 1; centre.add(imgRose,  gbc);
        gbc.gridx = 2; centre.add(imgRouge, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0; centre.add(btnBleu,  gbc);
        gbc.gridx = 1; centre.add(btnRose,  gbc);
        gbc.gridx = 2; centre.add(btnRouge, gbc);

        root.add(centre, BorderLayout.CENTER);

        // bas : Annuler / Valider
        JPanel bas = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        bas.setOpaque(false);

        btnAnnuler = new JButton("Annuler mise");
        styleButton(btnAnnuler, new Color(90, 90, 90), Color.WHITE);
        btnValider = new JButton("Jouer →");
        styleButton(btnValider, new Color(183, 28, 28), Color.WHITE);
        btnValider.setEnabled(false);

        btnAnnuler.addActionListener(e -> {
            mise = 0;
            miseLabelDialog.setText("Mise actuelle : 0 €");
            soldeLabelDialog.setText("Solde : " + solde + " €");
            btnValider.setEnabled(false);
        });

        btnValider.addActionListener(e -> {
            if (mise <= 0) { JOptionPane.showMessageDialog(bettingDialog, "Misez au moins un jeton !"); return; }
            bettingDialog.setVisible(false);
            lancerPartie();
        });

        bas.add(btnAnnuler);
        bas.add(btnValider);
        root.add(bas, BorderLayout.SOUTH);

        bettingDialog.add(root);
    }

    /** Crée un label image + texte pour un jeton */
    private JLabel loadJetonLabel(String path, String texte) {
        JLabel lbl;
        try {
            ImageIcon raw = new ImageIcon(path);
            Image scaled  = raw.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
            lbl = new JLabel(texte, new ImageIcon(scaled), SwingConstants.CENTER);
        } catch (Exception ex) {
            lbl = new JLabel(texte, SwingConstants.CENTER);
        }
        lbl.setVerticalTextPosition(SwingConstants.BOTTOM);
        lbl.setHorizontalTextPosition(SwingConstants.CENTER);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Georgia", Font.BOLD, 12));
        return lbl;
    }

    /** Crée un bouton "+Xjetons" */
    private JButton jetonButton(int valeur, Color couleur) {
        JButton btn = new JButton("+ " + valeur + " €");
        btn.setBackground(couleur);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Georgia", Font.BOLD, 13));
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.addActionListener(e -> {
            if (mise + valeur > solde) {
                JOptionPane.showMessageDialog(bettingDialog, "Solde insuffisant !");
                return;
            }
            mise += valeur;
            miseLabelDialog.setText("Mise actuelle : " + mise + " €");
            soldeLabelDialog.setText("Solde : " + (solde - mise) + " €");
            btnValider.setEnabled(true);
        });
        return btn;
    }

    /** Met à jour les labels dans la dialog (au retour d'une partie) */
    private void updateBettingUI() {
        refreshInfoLabel();
        if (soldeLabelDialog != null) {
            soldeLabelDialog.setText("Solde : " + solde + " €");
            miseLabelDialog.setText("Mise actuelle : 0 €");
        }
    }

    private void refreshInfoLabel() {
        if (infoLabel != null)
            infoLabel.setText("  Solde : " + solde + " €    |    Mise : " + mise + " €  ");
    }

    // ── Logique de jeu ──────────────────────────────────────────────────
    private void lancerPartie() {
        solde -= mise;          // on déduit la mise du solde
        partieFinie = false;
        startGame();
        hitButton.setEnabled(true);
        stayButton.setEnabled(true);
        refreshInfoLabel();
        gamePanel.repaint();
    }

    public void startGame() {
        buildDeck();
        shuffleDeck();

        dealerHand = new ArrayList<>();
        dealerSum  = dealerAceCount = 0;
        hiddenCard = deck.remove(deck.size() - 1);
        dealerSum += hiddenCard.getValue();
        dealerAceCount += hiddenCard.isAce() ? 1 : 0;

        Card card = deck.remove(deck.size() - 1);
        dealerSum += card.getValue();
        dealerAceCount += card.isAce() ? 1 : 0;
        dealerHand.add(card);

        playerHand  = new ArrayList<>();
        playerSum   = playerAceCount = 0;
        for (int i = 0; i < 2; i++) {
            card = deck.remove(deck.size() - 1);
            playerSum += card.getValue();
            playerAceCount += card.isAce() ? 1 : 0;
            playerHand.add(card);
        }
    }

    public void buildDeck() {
        deck = new ArrayList<>();
        String[] values = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
        String[] types  = {"C","D","H","S"};
        for (String t : types) for (String v : values) deck.add(new Card(v, t));
    }

    public void shuffleDeck() {
        for (int i = 0; i < deck.size(); i++) {
            int j = random.nextInt(deck.size());
            Card tmp = deck.get(i); deck.set(i, deck.get(j)); deck.set(j, tmp);
        }
    }

    public int reducePlayerAce() {
        while (playerSum > 21 && playerAceCount > 0) { playerSum -= 10; playerAceCount--; }
        return playerSum;
    }

    public int reduceDealerAce() {
        while (dealerSum > 21 && dealerAceCount > 0) { dealerSum -= 10; dealerAceCount--; }
        return dealerSum;
    }

    // ── Utilitaire style bouton ─────────────────────────────────────────
    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Georgia", Font.BOLD, 14));
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BlackJack::new);
    }
}
