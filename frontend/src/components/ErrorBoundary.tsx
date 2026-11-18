import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Button } from './ui';

interface State { failed: boolean }

/** Last line of defence: shows a recovery screen instead of a blank page if a screen crashes while rendering. */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State { return { failed: true }; }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Only the technical detail goes to the console; nothing from the screen's data is logged.
    console.error('Render error', error.message, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="grid min-h-full place-items-center px-4">
        <div className="card max-w-sm p-6 text-center">
          <h1 className="text-lg font-semibold text-white">Something went wrong</h1>
          <p className="mt-2 text-sm text-ink-300">The screen could not be shown. Reloading usually fixes it.</p>
          <Button className="mt-4" onClick={() => window.location.reload()}>Reload</Button>
        </div>
      </div>
    );
  }
}
