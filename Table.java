
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class Table {

    private static final int TIME_QUANTUM = 3;

    public Table(int process_num, List<Process> processList,
                 Object selectedItem) {

        SwingUtilities.invokeLater(() -> {

            if (processList == null || processList.isEmpty()) {
                JOptionPane.showMessageDialog(
                        null, "No processes are available.",
                        "Input Error", JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            List<Process> processes = new ArrayList<>(processList);
            final int actualProcessNum = processes.size();

            String algorithm = selectedItem == null
                    ? "FCFS" : selectedItem.toString().trim();

            // Validate and reset calculated values.
            for (Process p : processes) {
                if (p.burstTime <= 0 || p.arrivalTime < 0) {
                    JOptionPane.showMessageDialog(
                            null,
                            "Burst time must be positive and arrival time "
                                    + "cannot be negative.",
                            "Invalid Process Data",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                p.startTime = -1;
                p.completionTime = 0;
                p.waitingTime = 0;
                p.turnaroundTime = 0;
            }

            List<String> timeline = new ArrayList<>();

            if (algorithm.equals("Round Robin")) {
                runRoundRobin(processes, timeline);
            } else {
                runNonPreemptive(processes, timeline, algorithm);
            }

            // Calculate turnaround and waiting times.
            for (Process p : processes) {
                p.turnaroundTime = p.completionTime - p.arrivalTime;
                p.waitingTime = p.turnaroundTime - p.burstTime;
            }

            // Sort the display list by process ID.
            processes.sort(Comparator.comparingInt(p -> p.pid));

            JFrame frame = new JFrame("OS Project - Scheduling Results");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1100, 700);
            frame.setMinimumSize(new Dimension(700, 450));
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout());

            // Load background image using a relative path.
            BufferedImage backgroundImage = null;

            try {
                File imageFile = new File("os_background.jpeg");

                if (imageFile.isFile()) {
                    backgroundImage = ImageIO.read(imageFile);
                }

                if (backgroundImage == null) {
                    java.net.URL imageUrl =
                            Table.class.getResource("/os_background.jpeg");

                    if (imageUrl != null) {
                        backgroundImage = ImageIO.read(imageUrl);
                    }
                }
            } catch (IOException e) {
                System.err.println(
                        "Could not load background image: " + e.getMessage()
                );
            }

            final BufferedImage finalBackgroundImage = backgroundImage;

            JPanel backgroundPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);

                    if (finalBackgroundImage != null) {
                        g.drawImage(
                                finalBackgroundImage,
                                0, 0, getWidth(), getHeight(), this
                        );
                    }
                }
            };

            JLabel title = new JLabel(
                    "Scheduling Results - " + algorithm,
                    SwingConstants.CENTER
            );
            title.setFont(new Font("Arial", Font.BOLD, 22));
            title.setBorder(
                    BorderFactory.createEmptyBorder(12, 5, 12, 5)
            );

            boolean priorityMode = algorithm.equals("Priority");

            String secondColumn = priorityMode
                    ? "Priority" : "Arrival Time";

            String[] columnNames = {
                    "Process ID",
                    secondColumn,
                    "Burst Time",
                    "Start Time",
                    "Completion Time",
                    "Waiting Time",
                    "Turnaround Time"
            };

            Object[][] data = new Object[actualProcessNum][7];

            double totalWaiting = 0;
            double totalTurnaround = 0;

            for (int i = 0; i < actualProcessNum; i++) {
                Process p = processes.get(i);

                data[i][0] = "P" + p.pid;
                data[i][1] = priorityMode ? p.priority : p.arrivalTime;
                data[i][2] = p.burstTime;
                data[i][3] = p.startTime;
                data[i][4] = p.completionTime;
                data[i][5] = p.waitingTime;
                data[i][6] = p.turnaroundTime;

                totalWaiting += p.waitingTime;
                totalTurnaround += p.turnaroundTime;
            }

            DefaultTableModel processModel =
                    new DefaultTableModel(data, columnNames) {
                        @Override
                        public boolean isCellEditable(int row, int column) {
                            return false;
                        }
                    };

            JTable processTable = new JTable(processModel);
            styleTable(processTable, new Color(220, 230, 250));

            JScrollPane processScrollPane = new JScrollPane(processTable);
            processScrollPane.setBorder(
                    BorderFactory.createTitledBorder("Process Details")
            );

            // Build the Gantt chart.
            int timelineSize = timeline.size();
            String[] ganttColumns = new String[timelineSize];
            Object[][] ganttData = new Object[1][timelineSize];

            for (int i = 0; i < timelineSize; i++) {
                ganttColumns[i] = String.valueOf(i);
                ganttData[0][i] = timeline.get(i);
            }

            DefaultTableModel ganttModel =
                    new DefaultTableModel(ganttData, ganttColumns) {
                        @Override
                        public boolean isCellEditable(int row, int column) {
                            return false;
                        }
                    };

            JTable ganttTable = new JTable(ganttModel) {
                @Override
                public Component prepareRenderer(
                        TableCellRenderer renderer, int row, int column) {

                    Component c = super.prepareRenderer(
                            renderer, row, column
                    );

                    Object value = getValueAt(row, column);
                    String process = value == null ? "" : value.toString();

                    if (process.equals("Idle")) {
                        c.setBackground(Color.LIGHT_GRAY);
                        c.setForeground(Color.BLACK);
                        return c;
                    }

                    try {
                        int pid = Integer.parseInt(process.substring(1));

                        Color[] colors = {
                                new Color(255, 150, 150),
                                new Color(150, 255, 150),
                                new Color(150, 180, 255),
                                new Color(255, 235, 130),
                                new Color(255, 190, 120),
                                new Color(210, 170, 255),
                                new Color(140, 230, 230),
                                new Color(255, 190, 220),
                                new Color(200, 200, 200),
                                new Color(180, 220, 170)
                        };

                        c.setBackground(
                                colors[Math.floorMod(pid - 1, colors.length)]
                        );
                    } catch (NumberFormatException
                             | StringIndexOutOfBoundsException ex) {
                        c.setBackground(Color.WHITE);
                    }

                    c.setForeground(Color.BLACK);
                    return c;
                }
            };

            ganttTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            ganttTable.setRowHeight(35);

            for (int i = 0; i < ganttTable.getColumnCount(); i++) {
                ganttTable.getColumnModel()
                        .getColumn(i)
                        .setPreferredWidth(65);
            }

            JTableHeader ganttHeader = ganttTable.getTableHeader();
            ganttHeader.setReorderingAllowed(false);

            JScrollPane ganttScrollPane = new JScrollPane(ganttTable);
            ganttScrollPane.setBorder(
                    BorderFactory.createTitledBorder(
                            "Gantt Chart (columns show start times)"
                    )
            );

            double avgWaiting = totalWaiting / actualProcessNum;
            double avgTurnaround = totalTurnaround / actualProcessNum;

            JLabel statistics = new JLabel(
                    String.format(
                            "Average Waiting Time: %.2f"
                                    + "     Average Turnaround Time: %.2f"
                                    + "     Total Execution Time: %d",
                            avgWaiting, avgTurnaround, timelineSize
                    ),
                    SwingConstants.CENTER
            );

            statistics.setFont(new Font("Arial", Font.BOLD, 14));
            statistics.setBorder(
                    BorderFactory.createEmptyBorder(10, 5, 10, 5)
            );

            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(ganttScrollPane, BorderLayout.CENTER);
            bottomPanel.add(statistics, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    processScrollPane,
                    bottomPanel
            );
            splitPane.setResizeWeight(0.55);

            backgroundPanel.add(title, BorderLayout.NORTH);
            backgroundPanel.add(splitPane, BorderLayout.CENTER);

            frame.setContentPane(backgroundPanel);
            frame.setVisible(true);
        });
    }

    // FCFS, non-preemptive SJF, and non-preemptive Priority.
    private static void runNonPreemptive(
            List<Process> processes,
            List<String> timeline,
            String algorithm) {

        List<Process> remaining = new ArrayList<>(processes);
        int currentTime = 0;

        while (!remaining.isEmpty()) {
            Process selected = null;

            for (Process p : remaining) {
                if (p.arrivalTime > currentTime) {
                    continue;
                }

                if (selected == null) {
                    selected = p;
                } else if (algorithm.equals("SJF")) {
                    if (p.burstTime < selected.burstTime
                            || (p.burstTime == selected.burstTime
                            && p.arrivalTime < selected.arrivalTime)) {
                        selected = p;
                    }
                } else if (algorithm.equals("Priority")) {
                    // Lower numerical value means higher priority.
                    if (p.priority < selected.priority
                            || (p.priority == selected.priority
                            && p.arrivalTime < selected.arrivalTime)) {
                        selected = p;
                    }
                } else {
                    // FCFS.
                    if (p.arrivalTime < selected.arrivalTime
                            || (p.arrivalTime == selected.arrivalTime
                            && p.pid < selected.pid)) {
                        selected = p;
                    }
                }
            }

            if (selected == null) {
                timeline.add("Idle");
                currentTime++;
                continue;
            }

            selected.startTime = currentTime;

            for (int t = 0; t < selected.burstTime; t++) {
                timeline.add("P" + selected.pid);
            }

            currentTime += selected.burstTime;
            selected.completionTime = currentTime;
            remaining.remove(selected);
        }
    }

    // Round Robin with arrival-time handling.
    private static void runRoundRobin(
            List<Process> processes,
            List<String> timeline) {

        List<Process> arrivalOrder = new ArrayList<>(processes);

        arrivalOrder.sort(
                Comparator.comparingInt((Process p) -> p.arrivalTime)
                        .thenComparingInt(p -> p.pid)
        );

        Map<Process, Integer> remainingTime = new IdentityHashMap<>();

        for (Process p : arrivalOrder) {
            remainingTime.put(p, p.burstTime);
        }

        Deque<Process> readyQueue = new ArrayDeque<>();

        int currentTime = 0;
        int nextProcess = 0;
        int completed = 0;

        while (completed < arrivalOrder.size()) {

            while (nextProcess < arrivalOrder.size()
                    && arrivalOrder.get(nextProcess).arrivalTime
                    <= currentTime) {
                readyQueue.addLast(arrivalOrder.get(nextProcess));
                nextProcess++;
            }

            if (readyQueue.isEmpty()) {
                timeline.add("Idle");
                currentTime++;
                continue;
            }

            Process p = readyQueue.removeFirst();

            if (p.startTime == -1) {
                p.startTime = currentTime;
            }

            int remaining = remainingTime.get(p);
            int runTime = Math.min(TIME_QUANTUM, remaining);

            for (int t = 0; t < runTime; t++) {
                timeline.add("P" + p.pid);
            }

            currentTime += runTime;
            remaining -= runTime;
            remainingTime.put(p, remaining);

            while (nextProcess < arrivalOrder.size()
                    && arrivalOrder.get(nextProcess).arrivalTime
                    <= currentTime) {
                readyQueue.addLast(arrivalOrder.get(nextProcess));
                nextProcess++;
            }

            if (remaining > 0) {
                readyQueue.addLast(p);
            } else {
                p.completionTime = currentTime;
                completed++;
            }
        }
    }

    private static void styleTable(JTable table, Color baseColor) {

        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(70, 90, 130));
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Arial", Font.BOLD, 12));

        table.setDefaultRenderer(Object.class,
                new DefaultTableCellRenderer() {

                    @Override
                    public Component getTableCellRendererComponent(
                            JTable table, Object value,
                            boolean isSelected, boolean hasFocus,
                            int row, int column) {

                        Component c = super.getTableCellRendererComponent(
                                table, value, isSelected, hasFocus,
                                row, column
                        );

                        if (!isSelected) {
                            c.setBackground(
                                    row % 2 == 0 ? baseColor : Color.WHITE
                            );
                        }

                        return c;
                    }
                });
    }
}
