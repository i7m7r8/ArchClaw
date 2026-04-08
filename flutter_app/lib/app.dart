import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

// TODO: Create screen widgets
// import 'screens/home/home_screen.dart';
// import 'screens/setup/setup_wizard.dart';
// import 'screens/dashboard/dashboard_screen.dart';
// import 'screens/terminal/terminal_screen.dart';
// import 'screens/settings/settings_screen.dart';

final router = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const PlaceholderScreen('Home'),
    ),
    GoRoute(
      path: '/setup',
      builder: (context, state) => const PlaceholderScreen('Setup Wizard'),
    ),
    GoRoute(
      path: '/dashboard',
      builder: (context, state) => const PlaceholderScreen('Dashboard'),
    ),
    GoRoute(
      path: '/terminal',
      builder: (context, state) => const PlaceholderScreen('Terminal'),
    ),
    GoRoute(
      path: '/settings',
      builder: (context, state) => const PlaceholderScreen('Settings'),
    ),
  ],
);

class PlaceholderScreen extends StatelessWidget {
  final String title;
  
  const PlaceholderScreen(this.title, {super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: Text(
          '$title - Coming Soon',
          style: Theme.of(context).textTheme.headlineMedium,
        ),
      ),
    );
  }
}
