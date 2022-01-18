#nullable enable

using Microsoft.AspNetCore.Http;
using System.Threading.Tasks;

namespace UbisoftConnectProxy
{
    public interface IProxy
    {
        Task ForwardAsync(HttpContext context);
    }
}
