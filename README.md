# The Solo Leveling System: xLog

## Introduction

**xLog** is a command-line–based task management tool that turns everyday responsibilities into an immersive, RPG-style progression system. Your life is divided into **four domains**, each comprised of **four distinct elements**, allowing precise tracking of personal growth. Completing tasks awards XP to both a **major** and a **minor** element, offering clear visibility into how you develop habits and skills over time.

Embodying the spirit of **solo leveling**, xLog emphasizes that the greatest challenge is overcoming your own limits—it’s finally **you vs. you**.

## Features

- **GUI**: Lightweight interface.
- **Persistent Storage**: Robust SQLite backend for tasks and XP.
- **Progression Ranks**: Eight levels from **Rookie** to **Legend** with color-coded badges.
- **Task Categories**: Support for **Quick**, **Session**, and **Grind** tasks.
- **Element Focus**: Optionally mark an element as focus to earn a **10% XP bonus**.
- **Streak Mechanics**: Up to **+20% XP** for consecutive completions, plus **–40% XP** penalty for overdue tasks.
- **Daily Login Bonus**: Make sure to name a Element "Discipline" to get a daily login bonus.!

## Getting Started

### Prerequisites

- C++17–compatible compiler (e.g., GCC, Clang)
- SQLite3 development library and headers
- CMake or Make for build automation

### Installation

Clone the repository and run the installer script:

open folder in cmd.

set env var.

`set JAVAFX_LIB="C:\javafx-sdk-21.0.7\lib"`

compile with javac

`javac --module-path "%JAVAFX_LIB%" --add-modules javafx.controls -classpath ".;sqlite-jdbc-3.50.3.0.jar" Main.java`

run `run.bat`

## Leveling System

### Ranks & XP Thresholds

| Rank | Name        | Color  | Profile XP Needed | Approx. Time (@5h/day) |
|-----:|-------------|--------|-------------------|------------------------:|
|    0 | Rookie      | White  | 0                 | 0 days                  |
|    1 | Explorer    | Gray   | 1,712             | 1 month                 |
|    2 | Crafter     | Yellow | 6,847             | 3 months                |
|    3 | Strategist  | Orange | 15,408            | 7 months                |
|    4 | Expert      | Green  | 27,387            | 1 year 2 months         |
|    5 | Architect   | Blue   | 42,782            | 2 years                 |
|    6 | Elite       | Purple | 61,594            | 3 years                 |
|    7 | Master      | Red    | 83,823            | 4 years                 |
|    8 | Legend      | Black  | 109,500           | 5 years                 |

### Task Types & Base XP

| Type    | Major Base XP | Minor Base XP |
|:--------|--------------:|--------------:|
| Quick   | 10            | 5             |
| Session | 60            | 30            |
| Grind   | 125           | 75            |

### XP Modifiers

- **Focus Bonus:** +10% if the element is marked as focus.
- **Streak Bonus:** +1% per consecutive day (capped at +20%).
- **Overdue Penalty:** –40% if completing after the scheduled date.

*Example:* A `Session` task with base 60 XP, 15-day streak, in focus:
```
60 × (1 + 15/100) × 1.1 ≈ 75.9 → 76 XP awarded
```

## Contributing

Contributions are welcome! To propose changes:

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/YourFeature`
3. Commit your improvements: `git commit -m 'Add feature description'`
4. Push to your branch: `git push origin feature/YourFeature`
5. Open a pull request.






