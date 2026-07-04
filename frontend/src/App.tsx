import { Navigate, Route, Routes } from 'react-router-dom';
import { getCurrentMembership, getStoredEmployee, getToken, isManagerRole } from './api';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import CreateTeam from './pages/CreateTeam';
import Tasks from './pages/Tasks';
import Employees from './pages/Employees';
import Tags from './pages/Tags';
import Stats from './pages/Stats';
import Admin from './pages/Admin';
import Join from './pages/Join';

function RequireAuth({ children }: { children: JSX.Element }) {
  if (!getToken()) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireTeam({ children }: { children: JSX.Element }) {
  const employee = getStoredEmployee();
  if (employee && !employee.admin && employee.memberships.length === 0) {
    return <Navigate to="/create-team" replace />;
  }
  return children;
}

function HomeRoute() {
  const employee = getStoredEmployee();
  if (employee?.admin) {
    return <Navigate to="/admin" replace />;
  }
  return <Tasks />;
}

function RequireManager({ children }: { children: JSX.Element }) {
  const employee = getStoredEmployee();
  if (!employee || !isManagerRole(getCurrentMembership(employee)?.role)) {
    return <Navigate to="/" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const employee = getStoredEmployee();
  if (!employee?.admin) {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/join/:token" element={<Join />} />
      <Route
        path="/create-team"
        element={
          <RequireAuth>
            <CreateTeam />
          </RequireAuth>
        }
      />
      <Route
        path="/"
        element={
          <RequireAuth>
            <RequireTeam>
              <Layout />
            </RequireTeam>
          </RequireAuth>
        }
      >
        <Route index element={<HomeRoute />} />
        <Route
          path="employees"
          element={
            <RequireManager>
              <Employees />
            </RequireManager>
          }
        />
        <Route
          path="tags"
          element={
            <RequireManager>
              <Tags />
            </RequireManager>
          }
        />
        <Route path="stats" element={<Stats />} />
        <Route
          path="admin"
          element={
            <RequireAdmin>
              <Admin />
            </RequireAdmin>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
