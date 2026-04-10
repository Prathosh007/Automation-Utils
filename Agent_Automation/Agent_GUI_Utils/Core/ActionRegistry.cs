using GuiAgentUtils.Actions;

namespace GuiAgentUtils.Core
{
    public class ActionRegistry
    {
        private readonly Dictionary<string, IAction> _actions;

        public ActionRegistry()
        {
            _actions = new Dictionary<string, IAction>(StringComparer.OrdinalIgnoreCase)
            {
                ["click"] = new ClickAction(),
                ["clickbutton"] = new ClickAction(),
                ["rightclick"] = new RightClickAction(),
                ["entertext"] = new EnterTextAction(),
                ["wait"] = new WaitAction(),
                ["screenshot"] = new ScreenshotAction(),
                ["doubleclick"] = new DoubleClickAction(),
                ["readtext"] = new ReadTextAction(),
                ["readgrid"] = new ReadGridAction(),
                ["readtooltip"] = new ReadTooltipAction(), // New tooltip action
                ["toggle"] = new ToggleAction(),
                ["select"] = new SelectAction(),
                ["checkappinstalled"] = new CheckAppInstalledAction(),
                ["checkservice"] = new CheckServiceAction(),
                ["readstatusbyrow"] = new ReadStatusByRowAction(),
                ["open"] = new AppOpenAction(),
                ["close"] = new AppCloseAction(),
                ["close_all"] = new AppCloseAllAction(),
                ["closeall"] = new AppCloseAllAction()
            };
        }

        public IAction GetAction(string actionName)
        {
            if (_actions.TryGetValue(actionName, out var action))
                return action;

            throw new NotSupportedException($"Action '{actionName}' is not supported");
        }

        public IEnumerable<string> GetSupportedActions()
        {
            return _actions.Keys;
        }

        public void RegisterAction(string actionName, IAction action)
        {
            _actions[actionName] = action;
        }
    }
}